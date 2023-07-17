package com.railwayteam.railways.content.custom_tracks.casing;

import com.jozufozu.flywheel.api.Material;
import com.jozufozu.flywheel.core.PartialModel;
import com.jozufozu.flywheel.core.materials.model.ModelData;
import com.jozufozu.flywheel.util.transform.TransformStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.railwayteam.railways.Railways;
import com.railwayteam.railways.mixin_interfaces.IHasTrackCasing;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.foundation.render.CachedBufferer;
import com.simibubi.create.foundation.utility.Iterate;
import com.simibubi.create.foundation.utility.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class CasingRenderUtils {

  private static final HashMap<Pair<PartialModel, SlabBlock>, PartialModel> reTexturedModels = new HashMap<>();

  public static void clearModelCache() {
    reTexturedModels.clear();
  }

  public static PartialModel reTexture(PartialModel model, SlabBlock block) {
    Pair<PartialModel, SlabBlock> key = Pair.of(model, block);
    if (!reTexturedModels.containsKey(key)) {
      BakedModel slabModel = Minecraft.getInstance().getModelManager().getBlockModelShaper().getBlockModel(block.defaultBlockState());
      BakedModel texturedCasing = new SpriteCopyingBakedModel(model.get(), slabModel);
      PartialModel texturedPartial = RuntimeFakePartialModel.make(Railways.asResource("runtime_casing"), texturedCasing);
      reTexturedModels.put(key, texturedPartial);
      return texturedPartial;
    }

    return reTexturedModels.get(key);
  }
  public static void renderBezierCasings(PoseStack ms, Level level, PartialModel texturedPartial, BlockState state, VertexConsumer vb, BezierConnection bc) {
    int heightDiff = Math.abs(bc.tePositions.get(false).getY() - bc.tePositions.get(true).getY());
    double shiftDown = ((IHasTrackCasing) bc).isAlternate() && heightDiff > 0 ? -0.25 : 0;
    if (heightDiff / bc.getLength() <= 4/30d) {
      for (Vec3 pos : casingPositions(bc)) {
        ms.pushPose();
        BlockPos tePosition = bc.tePositions.getFirst();
        // FIXME HERE
        int light = LevelRenderer.getLightColor(level, new BlockPos(pos).offset(tePosition));

        CachedBufferer.partial(texturedPartial, state)
            .translate(pos.x, pos.y, pos.z)
            .translate(0, shiftDown, 0)
            .scale(1.001f)
            .light(light)
            .renderInto(ms, vb);
        ms.popPose();
      }
    } else {
      ms.pushPose();
      BlockPos tePosition = bc.tePositions.getFirst();
      BezierConnection.SegmentAngles[] segments = bc.getBakedSegments();

      TransformStack.cast(ms)
          .nudge((int) tePosition.asLong());

      for (int i = 1; i < segments.length; i++) {
        if (i % 2 == 0) continue;
        BezierConnection.SegmentAngles segment = segments[i];
        int light = LevelRenderer.getLightColor(level, segment.lightPosition.offset(tePosition));
        // FIXME POSSIBLE JANK
        Matrix4f pose = segment.tieTransform.pose();
        pose.translate(new Vector3f(0, (i % 4) * 0.001f, 0));
        CachedBufferer.partial(texturedPartial, state)
            .mulPose(pose)
            .mulNormal(segment.tieTransform.normal())
            .translate(0, shiftDown, 0)
            .scale(1.02f)
            .light(light)
            .renderInto(ms, vb);

        for (boolean first : Iterate.trueAndFalse) {
          PoseStack.Pose transform = segment.railTransforms.get(first);
          // FIXME POSSIBLE JANK
          Matrix4f pose2 = transform.pose();
          pose2.translate(new Vector3f(0, (i % 4) * 0.001f, 0));
          CachedBufferer.partial(texturedPartial, state)
              .mulPose(pose2)
              .mulNormal(transform.normal())
              .translate(-0.5, shiftDown, 0)
              .light(light)
              .renderInto(ms, vb);
        }
      }
      ms.popPose();
    }
  }

  public static List<Vec3> casingPositions(BezierConnection bc) {
    List<Vec3> positions = new ArrayList<>();
    List<int[]> takenPositions = new ArrayList<>();
    for (BezierConnection.Segment segment : bc) {
      Vec3 pos1 = segment.position.add(segment.normal.scale(1.3));
      Vec3 pos2 = segment.position.add(segment.normal.scale(-1.3));

      float steps = 4;
      Vec3 stepVec = pos1.vectorTo(pos2).scale(1 / steps);

      Vec3 curPos = pos1;

      for (int i = 0; i <= steps; i++) {
        int x = (int) Math.floor(curPos.x);
        int z = (int) Math.floor(curPos.z);
        if (takenPositions.stream().noneMatch(pos -> pos[0] == x && pos[1] == z)) {
          takenPositions.add(new int[]{x, z});
          positions.add(new Vec3(x, curPos.y - (3 / 16d), z));
        }
        curPos = curPos.add(stepVec);
      }
    }
    return positions.stream().toList();
  }

  public static ModelData makeCasingInstance(PartialModel baseModel, SlabBlock slabBlock, Material<ModelData> mat) {
    PartialModel texturedPartial = reTexture(baseModel, slabBlock);
    return mat.getModel(texturedPartial, slabBlock.defaultBlockState()).createInstance();
  }
}
