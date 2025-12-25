package nl.orbinuity.dispenserspear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

public class DispenseSpearBehavior implements DispenseItemBehavior {
    public static final String dispensedSpearId = "dispenserspear:dispensed_spear";

    private static final net.minecraft.core.dispenser.DefaultDispenseItemBehavior DEFAULT_BEHAVIOR =
            new net.minecraft.core.dispenser.DefaultDispenseItemBehavior();

    @Override
    public ItemStack dispense(BlockSource source, @NonNull ItemStack stack) {
        Level level = source.level();
        if (level.isClientSide()) return stack;

        Direction direction = source.state().getValue(DispenserBlock.FACING);
        BlockPos sourcePos = source.pos();
        BlockPos targetPos = sourcePos.relative(direction);

        List<ItemDisplay> existingSpears = level.getEntitiesOfClass(
                ItemDisplay.class,
                new AABB(targetPos).inflate(0.5),
                entity -> entity.getTags().contains(dispensedSpearId) && entity.getTags().contains("origin:" + sourcePos.getX() + "," + sourcePos.getY() + "," + sourcePos.getZ())
        );

        List<LivingEntity> damageEntities = level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(targetPos).inflate(0.5)
        );

        if (!existingSpears.isEmpty()) {
            Item storedItem = Items.AIR;
            ItemDisplay existingSpear = existingSpears.getFirst();

            for (String tag : existingSpear.getTags()) {
                if (tag.startsWith("item_id:")) {
                    String targetId = tag.substring("item_id:".length());

                    for (Item candidate : ForgeRegistries.ITEMS.getValues()) {
                        String candidateId = Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(candidate)).toString();
                        if (candidateId.equals(targetId)) {
                            storedItem = candidate;
                            break;
                        }
                    }
                    break;
                }
            }

            if (storedItem == null || storedItem == Items.AIR) {
                level.players().get(0).displayClientMessage(
                        net.minecraft.network.chat.Component.literal("FUCK!"),
                        false
                );
                storedItem = stack.getItem();
            }

            for (ItemDisplay spear : existingSpears) {
                spear.discard();
            }

            if (stack.getCount() < stack.getMaxStackSize() && isSpear(stack.getItem())) {
                stack.grow(1);
            } else {
                ItemStack refundStack = new ItemStack(storedItem);
                BlockEntity be = source.blockEntity();

                if (be != null) {
                    be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                        ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, refundStack, false);
                        if (!remainder.isEmpty()) {
                            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                                    level, sourcePos.getX() + 0.5, sourcePos.getY() + 0.5, sourcePos.getZ() + 0.5, remainder
                            );
                            level.addFreshEntity(drop);
                        }
                    });
                }
            }
            return stack;
        }

        if (isSpear(stack.getItem())) {
            double x = targetPos.getX() + 0.5;
            double y = targetPos.getY() + 0.5;
            double z = targetPos.getZ() + 0.5;

            ItemStack item = stack.copy();
            item.setCount(1);

            ItemDisplay spearStand = new ItemDisplay(EntityType.ITEM_DISPLAY, level);

            CompoundTag rootNbt = new CompoundTag();

            Tag itemNbt = ItemStack.CODEC
                    .encodeStart(level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), item)
                    .getOrThrow(IllegalStateException::new);

            rootNbt.put("item", itemNbt);

            Matrix4f mathMatrix = getSpearMatrix(direction);

            mathMatrix.transpose();

            float[] matrixData = new float[16];
            mathMatrix.get(matrixData);

            rootNbt.put("transformation", transformList(matrixData));
            rootNbt.putString("item_display", "fixed");
            rootNbt.putString("CustomName", stack.getDisplayName().getString());

            spearStand.deserializeNBT(level.registryAccess(), rootNbt);

            spearStand.setPos(x, y, z);
            spearStand.addTag(dispensedSpearId);

            String cleanOrigin = "origin:" + sourcePos.getX() + "," + sourcePos.getY() + "," + sourcePos.getZ();
            spearStand.addTag(cleanOrigin);

            String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            spearStand.addTag("item_id:" + itemId);

            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", sourcePos.getX());
            posTag.putInt("Y", sourcePos.getY());
            posTag.putInt("Z", sourcePos.getZ());
            spearStand.getPersistentData().put("SourcePos", posTag);

            level.addFreshEntity(spearStand);

            for (LivingEntity damageEntity : damageEntities) {
                damageEntity.hurt(level.damageSources().trident(spearStand, null), 8.0f);
            }

            stack.shrink(1);
            return stack;
        } else {
            return DEFAULT_BEHAVIOR.dispense(source, stack);
        }
    }

    private boolean isSpear(Item item) {
        return item == Items.WOODEN_SPEAR || item == Items.STONE_SPEAR ||
                item == Items.COPPER_SPEAR || item == Items.GOLDEN_SPEAR ||
                item == Items.DIAMOND_SPEAR || item == Items.NETHERITE_SPEAR;
    }

    private Matrix4f getSpearMatrix(Direction facing) {
        Matrix4f matrix = new Matrix4f();
        matrix.rotateZ((float) Math.toRadians(45));

        Quaternionf facingRot = new Quaternionf();

        switch (facing) {
            case DOWN -> facingRot.rotateX((float) Math.PI);
            case UP -> {}
            case NORTH -> facingRot.rotateX((float) Math.toRadians(-90));
            case SOUTH -> facingRot.rotateX((float) Math.toRadians(90));
            case WEST -> facingRot.rotateZ((float) Math.toRadians(90));
            case EAST -> facingRot.rotateZ((float) Math.toRadians(-90));
        }

        Matrix4f finalMatrix = new Matrix4f();
        finalMatrix.rotate(facingRot);
        finalMatrix.mul(matrix);

        return finalMatrix;
    }

    public ListTag transformList(float[] transform) {
        ListTag transformation = new ListTag();
        for (float val : transform) {
            transformation.add(FloatTag.valueOf(val));
        }
        return transformation;
    }
}