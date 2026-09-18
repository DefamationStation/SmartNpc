package com.pla.smart_npc.client.renderer;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.pla.smart_npc.clazz.FakePlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FakePlayerTextureUtils {
    private static final Map<UUID, SkinType> SKIN_TYPE_CACHE = new ConcurrentHashMap<>();

    private FakePlayerTextureUtils() {
    }

    public static SkinType getPlayerSkinType(GameProfile profile) {
        if (!isComplete(profile)) {
            return SkinType.DEFAULT;
        }

        return SKIN_TYPE_CACHE.computeIfAbsent(profile.getId(), ignored ->
                Minecraft.getInstance().getSkinManager().getInsecureSkin(profile).model() == PlayerSkin.Model.SLIM
                        ? SkinType.SLIM
                        : SkinType.DEFAULT);
    }

    public static ResourceLocation getPlayerSkin(FakePlayer entity) {
        return getTexture(entity, MinecraftProfileTexture.Type.SKIN).orElseGet(() -> {
            GameProfile profile = entity.getProfile();
            return isComplete(profile)
                    ? DefaultPlayerSkin.get(profile).texture()
                    : DefaultPlayerSkin.getDefaultTexture();
        });
    }

    public static Optional<ResourceLocation> getPlayerCape(FakePlayer entity) {
        return getTexture(entity, MinecraftProfileTexture.Type.CAPE);
    }

    private static Optional<ResourceLocation> getTexture(FakePlayer entity, MinecraftProfileTexture.Type type) {
        if (entity.isTextureAvailable(type)) {
            return Optional.ofNullable(entity.getTexture(type));
        }

        GameProfile profile = entity.getProfile();
        if (!isComplete(profile)) {
            return Optional.empty();
        }

        PlayerSkin skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(profile);
        ResourceLocation location = switch (type) {
            case SKIN -> skin.texture();
            case CAPE -> skin.capeTexture();
            case ELYTRA -> skin.elytraTexture();
        };
        if (location != null) {
            entity.setTexture(type, location);
        }
        return Optional.ofNullable(location);
    }

    private static boolean isComplete(GameProfile profile) {
        return profile != null && profile.getId() != null && !StringUtil.isNullOrEmpty(profile.getName());
    }

    public enum SkinType {
        DEFAULT,
        SLIM
    }
}
