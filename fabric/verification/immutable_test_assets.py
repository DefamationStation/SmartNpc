"""Verified shared bytes for disposable Minecraft tests.

Inputs are copied once into a private content-addressed store. Test files may
hardlink only those frozen cache bytes, never an installed/original input.
Existing files are verified, never overwritten or silently repaired. Worlds,
configuration and logs must remain ordinary independent copies. Consumers must
treat linked assets as read-only; writes through a link invalidate the cache
and fail later verification. Use copies for anything the game may edit.
"""
from __future__ import annotations

from contextlib import contextmanager
from dataclasses import dataclass
import errno
import hashlib
import os
from pathlib import Path
import re
import tempfile
import time


class AssetError(RuntimeError):
    pass


class AssetCorruptionError(AssetError):
    pass


class AssetChangedError(AssetError):
    pass


class AssetConflictError(AssetError):
    pass


class AssetLockTimeout(AssetError):
    pass


@dataclass(frozen=True)
class FrozenAsset:
    path: Path
    sha256: str
    size: int


@dataclass(frozen=True)
class Placement:
    destination: Path
    cache_path: Path
    sha256: str
    size: int
    method: str


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _identity(path: Path) -> tuple:
    stat = path.stat()
    return stat.st_dev, stat.st_ino, stat.st_size, stat.st_mtime_ns


def _normal_path(path: Path) -> Path:
    value = os.fspath(path)
    if os.name == "nt":
        # Non-strict Windows resolution may retain this prefix while another
        # process is creating a parent directory. Compare equivalent ordinary
        # drive/UNC names consistently; leave other namespaces untouched.
        if value.startswith("\\\\?\\UNC\\"):
            value = "\\\\" + value[8:]
        elif value.startswith("\\\\?\\") and len(value) >= 7 and value[5:7] == ":\\":
            value = value[4:]
    return Path(value)


def _entry(path: Path) -> Path:
    # Resolve directory aliases, not a final file being published concurrently.
    # Containment concerns its parent, independently of the new link's existence.
    return _normal_path(path.parent.resolve()) / path.name


def _copy_verified(source: Path, temporary: Path, digest: str, size: int) -> None:
    actual = hashlib.sha256()
    count = 0
    with source.open("rb") as reader, temporary.open("wb") as writer:
        for block in iter(lambda: reader.read(1024 * 1024), b""):
            writer.write(block)
            actual.update(block)
            count += len(block)
        writer.flush()
        os.fsync(writer.fileno())
    if count != size or actual.hexdigest() != digest:
        raise AssetChangedError("Input bytes changed while copying a frozen asset")


class AssetStore:
    def __init__(self, workspace: Path, directory: Path | None = None, *, lock_timeout: float = 60):
        self.workspace = _normal_path(Path(workspace).resolve())
        self.private = self.workspace / ".verification"
        if directory is None:
            directory = self.private / "immutable-test-assets"
        else:
            directory = Path(directory)
            if not directory.is_absolute():
                directory = self.workspace / directory
        directory = _normal_path(Path(os.path.abspath(directory)))
        if not directory.is_relative_to(self.private) or directory == self.private:
            raise ValueError("Asset store must be inside workspace .verification")
        directory.mkdir(parents=True, exist_ok=True)
        # An explicitly configured private cache junction may live on another
        # volume. Descendants must remain inside this resolved frozen store.
        self.directory = _normal_path(directory.resolve())
        self.lock_timeout = max(0, float(lock_timeout))

    @staticmethod
    def _digest(value: str) -> str:
        value = value.lower()
        if not re.fullmatch(r"[0-9a-f]{64}", value):
            raise ValueError("Expected SHA-256 must contain 64 hexadecimal characters")
        return value

    def _blob(self, digest: str) -> Path:
        path = self.directory / "sha256" / digest[:2] / digest
        entry = _entry(path)
        if not entry.is_relative_to(self.directory):
            raise ValueError(f"Asset cache shard escaped its private store: {entry.parts!r} outside {self.directory.parts!r}")
        return path

    def _destination(self, value: Path) -> Path:
        path = Path(value)
        if not path.is_absolute():
            path = self.workspace / path
        path = _normal_path(Path(os.path.abspath(path)))
        if not path.is_relative_to(self.private) or path == self.private:
            raise ValueError("Asset destination must be inside workspace .verification")
        # Closed archives outside this private root are read-only evidence, not
        # new output directories. A junction must not redirect writes to inputs.
        if not _entry(path).is_relative_to(_normal_path(self.private.resolve())):
            raise ValueError("Asset destination resolves outside workspace .verification")
        return path

    @contextmanager
    def _lock(self, target: Path):
        lock = target.with_name(target.name + ".asset-lock")
        deadline = time.monotonic() + self.lock_timeout
        while True:
            try:
                lock.mkdir()
                break
            except FileExistsError:
                if time.monotonic() >= deadline:
                    raise AssetLockTimeout(f"Asset publication lock unavailable: {lock.name}")
                time.sleep(0.025)
        try:
            yield
        finally:
            lock.rmdir()

    @staticmethod
    @contextmanager
    def _temporary(parent: Path):
        descriptor, name = tempfile.mkstemp(prefix=".asset-", suffix=".pending", dir=parent)
        os.close(descriptor)
        path = Path(name)
        try:
            yield path
        finally:
            path.unlink(missing_ok=True)

    @staticmethod
    def _verify(path: Path, digest: str, size: int, error=AssetCorruptionError):
        if path.is_symlink() or not path.is_file():
            raise error(f"Asset is not an independent regular file: {path.name}")
        if path.stat().st_size != size or sha256_file(path) != digest:
            raise error(f"Asset bytes differ from the expected SHA-256: {path.name}")

    def freeze(self, source: Path, *, expected_sha256: str | None = None) -> FrozenAsset:
        source = Path(source).absolute()
        source = source.resolve(strict=True) if source.is_symlink() else _entry(source)
        if not source.is_file():
            raise ValueError("Asset input must be a regular file")
        expected = None if expected_sha256 is None else self._digest(expected_sha256)
        before = _identity(source)
        digest = sha256_file(source)
        if _identity(source) != before:
            raise AssetChangedError("Input changed while its SHA-256 was read")
        if expected is not None and digest != expected:
            raise AssetCorruptionError("Input does not match the expected SHA-256")
        size = before[2]
        blob = self._blob(digest)
        blob.parent.mkdir(parents=True, exist_ok=True)
        with self._lock(blob):
            if os.path.lexists(blob):
                self._verify(blob, digest, size)
            else:
                with self._temporary(blob.parent) as temporary:
                    _copy_verified(source, temporary, digest, size)
                    if _identity(source) != before:
                        raise AssetChangedError("Input changed before its frozen copy was published")
                    # All writers use the same digest lock. Readers can only
                    # see a complete verified file, never a partially copied jar.
                    os.replace(temporary, blob)
            if _identity(source) != before:
                raise AssetChangedError("Input changed during asset publication")
            self._verify(blob, digest, size)
        return FrozenAsset(blob, digest, size)

    def materialize(self, source: Path | FrozenAsset, destination: Path, *, expected_sha256: str | None = None) -> Placement:
        asset = source if isinstance(source, FrozenAsset) else self.freeze(source, expected_sha256=expected_sha256)
        digest = self._digest(asset.sha256)
        if expected_sha256 is not None and digest != self._digest(expected_sha256):
            raise AssetCorruptionError("Frozen asset does not match the expected SHA-256")
        blob = self._blob(digest)
        if _entry(asset.path) != _entry(blob):
            raise ValueError("Only a verified file in this frozen store can be linked")
        self._verify(blob, digest, asset.size)
        target = self._destination(destination)
        if _entry(target) == _entry(blob):
            raise ValueError("An asset destination cannot replace its frozen cache entry")
        target.parent.mkdir(parents=True, exist_ok=True)
        with self._lock(target):
            if os.path.lexists(target):
                self._verify(target, digest, asset.size, AssetConflictError)
                return Placement(target, blob, digest, asset.size, "existing")
            method = "hardlink"
            try:
                os.link(blob, target)
            except OSError as error:
                unsupported = {errno.EXDEV, errno.EPERM, errno.EACCES, errno.ENOTSUP,
                               errno.EOPNOTSUPP, errno.ENOSYS, errno.EMLINK}
                if error.errno not in unsupported and getattr(error, "winerror", None) not in {1, 5, 17, 50}:
                    raise
                method = "copy"
                with self._temporary(target.parent) as temporary:
                    _copy_verified(blob, temporary, digest, asset.size)
                    os.replace(temporary, target)
            self._verify(blob, digest, asset.size)
            self._verify(target, digest, asset.size)
            return Placement(target, blob, digest, asset.size, method)
