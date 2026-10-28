package net.dx12mc.mixin;

import java.util.ArrayList;
import java.util.List;
import net.dx12mc.DX12MC;
import net.dx12mc.pack.MaterialAtlas;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {

  @Inject(method = "upload", at = @At("RETURN"))
  private void dx12mc$recordSprites(SpriteLoader.Preparations preparations, CallbackInfo ci) {
    TextureAtlas atlas = (TextureAtlas) (Object) this;
    if (!TextureAtlas.LOCATION_BLOCKS.equals(atlas.location())) return;
    List<MaterialAtlas.Sprite> sprites = new ArrayList<>(preparations.regions().size());
    for (TextureAtlasSprite sprite : preparations.regions().values()) {
      sprites.add(
          new MaterialAtlas.Sprite(
              sprite.contents().name().toString(),
              sprite.getX(),
              sprite.getY(),
              sprite.contents().width(),
              sprite.contents().height()));
    }
    DX12MC
        .runtime()
        .atlasUploaded(
            new MaterialAtlas.Layout(
                atlas.getId(),
                preparations.width(),
                preparations.height(),
                preparations.mipLevel(),
                List.copyOf(sprites)));
  }
}
