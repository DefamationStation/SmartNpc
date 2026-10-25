"""Filesystem invariants only: temporary private roots, never game instances."""
from concurrent.futures import ThreadPoolExecutor
import errno
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch

import immutable_test_assets as assets


class ImmutableTestAssetsTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.workspace = Path(self.temporary.name)
        self.source = self.workspace / "installed.jar"
        self.data = b"original immutable Minecraft test bytes\0" * 1024
        self.source.write_bytes(self.data)
        self.digest = hashlib.sha256(self.data).hexdigest()
        self.store = assets.AssetStore(self.workspace, lock_timeout=5)

    def destination(self, name="mod.jar"):
        return self.workspace / ".verification" / "case" / name

    def assert_no_partial_files(self):
        self.assertFalse(list((self.workspace / ".verification").rglob("*.pending")))
        self.assertFalse(list((self.workspace / ".verification").rglob("*.asset-lock")))

    def test_frozen_bytes_are_copied_from_input_then_linked_only_from_cache(self):
        before = self.source.stat()
        frozen = self.store.freeze(self.source, expected_sha256=self.digest.upper())
        placed = self.store.materialize(frozen, self.destination())
        self.assertEqual(placed.sha256, self.digest)
        self.assertEqual(placed.destination.read_bytes(), self.data)
        self.assertFalse(os.path.samefile(self.source, frozen.path))
        self.assertFalse(os.path.samefile(self.source, placed.destination))
        self.assertEqual(os.path.samefile(frozen.path, placed.destination), placed.method == "hardlink")
        self.assertEqual(self.source.stat().st_mtime_ns, before.st_mtime_ns)
        self.assertEqual(self.source.read_bytes(), self.data)
        self.assert_no_partial_files()

    def test_replacing_input_cannot_change_old_test_bytes(self):
        first = self.store.materialize(self.source, self.destination("old.jar"))
        self.source.write_bytes(b"new installed version")
        second = self.store.materialize(self.source, self.destination("new.jar"))
        self.assertNotEqual(first.sha256, second.sha256)
        self.assertEqual(first.destination.read_bytes(), self.data)
        self.assertEqual(first.cache_path.read_bytes(), self.data)
        self.assertEqual(second.destination.read_bytes(), b"new installed version")

    def test_expected_hash_mismatch_does_not_publish_any_cache_blob(self):
        with self.assertRaises(assets.AssetCorruptionError):
            self.store.freeze(self.source, expected_sha256="0" * 64)
        self.assertFalse(list(self.store.directory.rglob(self.digest)))
        self.assertEqual(self.source.read_bytes(), self.data)

    def test_corrupt_cache_is_reported_and_never_silently_repaired(self):
        frozen = self.store.freeze(self.source)
        frozen.path.write_bytes(b"corrupted")
        with self.assertRaises(assets.AssetCorruptionError):
            self.store.freeze(self.source)
        self.assertEqual(frozen.path.read_bytes(), b"corrupted")
        self.assertEqual(self.source.read_bytes(), self.data)
        self.assert_no_partial_files()

    def test_existing_destination_with_different_bytes_is_not_overwritten(self):
        target = self.destination()
        target.parent.mkdir(parents=True)
        target.write_bytes(b"other verified case")
        with self.assertRaises(assets.AssetConflictError):
            self.store.materialize(self.source, target)
        self.assertEqual(target.read_bytes(), b"other verified case")
        self.assert_no_partial_files()

    def test_matching_existing_destination_is_verified_without_replacement(self):
        first = self.store.materialize(self.source, self.destination())
        before = first.destination.stat()
        second = self.store.materialize(self.source, self.destination())
        self.assertEqual(second.method, "existing")
        self.assertEqual(second.destination.stat().st_ino, before.st_ino)
        self.assertEqual(second.destination.stat().st_mtime_ns, before.st_mtime_ns)

    def test_cross_filesystem_link_failure_uses_verified_independent_copy(self):
        frozen = self.store.freeze(self.source)
        with patch.object(assets.os, "link", side_effect=OSError(errno.EXDEV, "other volume")):
            placed = self.store.materialize(frozen, self.destination())
        self.assertEqual(placed.method, "copy")
        self.assertFalse(os.path.samefile(placed.destination, frozen.path))
        self.assertEqual(placed.destination.read_bytes(), self.data)
        self.assert_no_partial_files()

    def test_failed_copy_fallback_never_publishes_partial_destination(self):
        frozen = self.store.freeze(self.source)

        def disk_full(source, temporary, digest, size):
            temporary.write_bytes(b"partial")
            raise OSError(errno.ENOSPC, "simulated full disk")

        with patch.object(assets.os, "link", side_effect=OSError(errno.EXDEV, "other volume")):
            with patch.object(assets, "_copy_verified", side_effect=disk_full):
                with self.assertRaises(OSError):
                    self.store.materialize(frozen, self.destination())
        self.assertFalse(self.destination().exists())
        self.assertEqual(frozen.path.read_bytes(), self.data)
        self.assertEqual(self.source.read_bytes(), self.data)
        self.assert_no_partial_files()

    def test_failed_cache_write_does_not_publish_or_truncate_source(self):
        def disk_full(source, temporary, digest, size):
            temporary.write_bytes(b"partial cache")
            raise OSError(errno.ENOSPC, "simulated full disk")

        with patch.object(assets, "_copy_verified", side_effect=disk_full):
            with self.assertRaises(OSError):
                self.store.freeze(self.source)
        self.assertFalse(self.store._blob(self.digest).exists())
        self.assertEqual(self.source.read_bytes(), self.data)
        self.assert_no_partial_files()

    def test_cache_is_invisible_until_complete_verified_bytes_are_published(self):
        writing, release = threading.Event(), threading.Event()
        original_copy = assets._copy_verified

        def paused_copy(source, temporary, digest, size):
            temporary.write_bytes(b"partial cache")
            writing.set()
            if not release.wait(5):
                raise TimeoutError("test observer did not release writer")
            original_copy(source, temporary, digest, size)

        with patch.object(assets, "_copy_verified", side_effect=paused_copy), ThreadPoolExecutor(max_workers=1) as pool:
            future = pool.submit(self.store.freeze, self.source)
            try:
                self.assertTrue(writing.wait(5))
                self.assertFalse(self.store._blob(self.digest).exists())
            finally:
                release.set()
            self.assertEqual(future.result().path.read_bytes(), self.data)
        self.assert_no_partial_files()

    def test_copy_fallback_destination_is_invisible_until_complete(self):
        frozen = self.store.freeze(self.source)
        writing, release = threading.Event(), threading.Event()
        original_copy = assets._copy_verified

        def paused_copy(source, temporary, digest, size):
            temporary.write_bytes(b"partial destination")
            writing.set()
            if not release.wait(5):
                raise TimeoutError("test observer did not release writer")
            original_copy(source, temporary, digest, size)

        with patch.object(assets.os, "link", side_effect=OSError(errno.EXDEV, "other volume")), \
                patch.object(assets, "_copy_verified", side_effect=paused_copy), ThreadPoolExecutor(max_workers=1) as pool:
            future = pool.submit(self.store.materialize, frozen, self.destination())
            try:
                self.assertTrue(writing.wait(5))
                self.assertFalse(self.destination().exists())
            finally:
                release.set()
            self.assertEqual(future.result().destination.read_bytes(), self.data)
        self.assert_no_partial_files()

    def test_source_mutation_during_copy_does_not_publish_stale_snapshot(self):
        original_copy = assets._copy_verified

        def external_mutation(source, temporary, digest, size):
            original_copy(source, temporary, digest, size)
            source.write_bytes(b"external replacement")

        with patch.object(assets, "_copy_verified", side_effect=external_mutation):
            with self.assertRaises(assets.AssetChangedError):
                self.store.freeze(self.source)
        self.assertFalse(list(self.store.directory.rglob(self.digest)))
        self.assert_no_partial_files()

    def test_concurrent_stores_publish_one_cache_blob_and_complete_outputs(self):
        def produce(index):
            store = assets.AssetStore(self.workspace, lock_timeout=5)
            return store.materialize(self.source, self.destination(f"worker-{index}.jar"))

        with ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(produce, range(16)))
        self.assertEqual(len({r.cache_path for r in results}), 1)
        self.assertTrue(all(r.destination.read_bytes() == self.data for r in results))
        self.assertEqual(self.source.read_bytes(), self.data)
        self.assert_no_partial_files()

    def test_concurrent_same_destination_is_idempotent_without_overwrite(self):
        def produce(_):
            return assets.AssetStore(self.workspace, lock_timeout=5).materialize(self.source, self.destination())

        with ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(produce, range(12)))
        self.assertEqual(sum(r.method != "existing" for r in results), 1)
        self.assertEqual(self.destination().read_bytes(), self.data)
        self.assert_no_partial_files()

    def test_separate_processes_share_the_atomic_store(self):
        environment = os.environ.copy()
        environment["PYTHONPATH"] = str(Path(__file__).parent) + os.pathsep + environment.get("PYTHONPATH", "")
        code = "from pathlib import Path; import sys; from immutable_test_assets import AssetStore; AssetStore(Path(sys.argv[1])).materialize(Path(sys.argv[2]),Path(sys.argv[3]))"
        processes = [subprocess.Popen([sys.executable, "-c", code, str(self.workspace), str(self.source), str(self.destination(f"process-{i}.jar"))],
                                      env=environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE) for i in range(4)]
        try:
            for process in processes:
                stdout, stderr = process.communicate(timeout=20)
                self.assertEqual(process.returncode, 0, (stdout + stderr).decode(errors="replace"))
        finally:
            for process in processes:
                if process.poll() is None:
                    process.kill()
                    process.communicate()
        self.assertTrue(all(self.destination(f"process-{i}.jar").read_bytes() == self.data for i in range(4)))
        self.assertEqual(len(list(self.store.directory.rglob(self.digest))), 1)
        self.assertEqual(self.source.read_bytes(), self.data)
        self.assert_no_partial_files()

    def test_corruption_through_a_consumer_link_is_detected_without_touching_input(self):
        first = self.store.materialize(self.source, self.destination())
        first.destination.write_bytes(b"consumer violated readonly contract")
        # A hardlink consumer must treat immutable assets as read-only. If it
        # does not, fail future verification rather than repairing old evidence.
        if first.method == "hardlink":
            with self.assertRaises(assets.AssetCorruptionError):
                self.store.materialize(self.source, self.destination("next.jar"))
            self.assertFalse(self.destination("next.jar").exists())
        else:
            self.assertEqual(first.cache_path.read_bytes(), self.data)
        self.assertEqual(self.source.read_bytes(), self.data)

    def test_stale_lock_times_out_without_removing_another_owners_lock(self):
        digest = self.store._blob(self.digest)
        digest.parent.mkdir(parents=True)
        lock = digest.with_name(digest.name + ".asset-lock")
        lock.mkdir()
        store = assets.AssetStore(self.workspace, lock_timeout=0)
        with self.assertRaises(assets.AssetLockTimeout):
            store.freeze(self.source)
        self.assertTrue(lock.is_dir())
        self.assertFalse(digest.exists())

    def test_forged_frozen_input_cannot_link_an_original_file(self):
        forged = assets.FrozenAsset(self.source, self.digest, len(self.data))
        with self.assertRaises(ValueError):
            self.store.materialize(forged, self.destination())
        self.assertFalse(self.destination().exists())

    def test_store_and_outputs_cannot_escape_private_verification_root(self):
        with self.assertRaises(ValueError):
            assets.AssetStore(self.workspace, self.workspace / "instances" / "cache")
        with self.assertRaises(ValueError):
            self.store.materialize(self.source, self.workspace / "instances" / "new.jar")
        self.assertFalse((self.workspace / "instances").exists())

    def test_bad_digest_cannot_be_used_as_a_cache_path(self):
        with self.assertRaises(ValueError):
            self.store.freeze(self.source, expected_sha256="../../other-file")
        self.assertEqual(self.source.read_bytes(), self.data)

    def test_cache_asset_cannot_be_its_own_materialization_target(self):
        frozen = self.store.freeze(self.source)
        with self.assertRaises(ValueError):
            self.store.materialize(frozen, frozen.path)
        self.assertEqual(frozen.path.read_bytes(), self.data)

    @unittest.skipUnless(os.name == "nt", "Windows device-prefix alias regression")
    def test_windows_extended_drive_aliases_use_the_same_private_store(self):
        extended = Path("\\\\?\\" + str(self.workspace))
        placed = assets.AssetStore(extended).materialize(self.source, self.destination())
        self.assertEqual(placed.destination.read_bytes(), self.data)
        self.assertTrue(placed.cache_path.is_relative_to(self.store.directory))


if __name__ == "__main__":
    unittest.main()
