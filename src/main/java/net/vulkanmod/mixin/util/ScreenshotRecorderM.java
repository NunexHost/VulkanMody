package net.vulkanmod.mixin.util;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;
import net.vulkanmod.gl.GlTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Screenshot.class)
public class ScreenshotRecorderM {

    @Overwrite
    public static NativeImage takeScreenshot(RenderTarget target) {
        int i = target.width;
        int j = target.height;

        NativeImage nativeimage = new NativeImage(i, j, false);
        GlTexture.bindTexture(target.getColorTextureId());

        nativeimage.downloadTexture(0, true);

        nativeimage.flipY();
        return nativeimage;
    }
}
