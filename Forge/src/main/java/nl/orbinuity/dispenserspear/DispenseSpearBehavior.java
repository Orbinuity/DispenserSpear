package nl.orbinuity.dispenserspear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
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

import java.util.*;

public class DispenseSpearBehavior implements DispenseItemBehavior {
    public static final String dispensedSpearId = DispenserSpear.MODID + ":dispensed_spear";
    public static final Map<String, Float> spearDamage = Map.of(
            "minecraft:wooden_spear", 3f,
            "minecraft:stone_spear", 4f,
            "minecraft:copper_spear", 4f,
            "minecraft:iron_spear", 5f,
            "minecraft:golden_spear", 3f,
            "minecraft:diamond_spear", 6f,
            "minecraft:netherite_spear", 7f
    );
    private static final DefaultDispenseItemBehavior DEFAULT_BEHAVIOR = new DefaultDispenseItemBehavior();

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

            Optional<String> itemId = existingSpear.getTags().stream()
                    .filter(s -> s.startsWith("item_id:"))
                    .findFirst();

            if (itemId.isPresent()) {
                for (Item candidate : ForgeRegistries.ITEMS.getValues()) {
                    String candidateId = Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(candidate)).toString();
                    if (candidateId.equals(itemId.get().substring("item_id:".length()))) {
                        storedItem = candidate;
                        break;
                    }
                }
            }

            if (storedItem == null || storedItem == Items.AIR) {
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
                            ItemEntity drop = new ItemEntity(
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
                    .encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), item)
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

            String itemId = Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(stack.getItem())).toString();
            spearStand.addTag("item_id:" + itemId);

            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", sourcePos.getX());
            posTag.putInt("Y", sourcePos.getY());
            posTag.putInt("Z", sourcePos.getZ());
            spearStand.getPersistentData().put("SourcePos", posTag);

            level.addFreshEntity(spearStand);

            for (LivingEntity damageEntity : damageEntities) {
                damageEntity.hurt(level.damageSources().trident(spearStand, null), spearDamage.get(itemId));
            }

            stack.shrink(1);
            return stack;
        } else {
            return DEFAULT_BEHAVIOR.dispense(source, stack);
        }
    }

    private boolean isSpear(Item item) {
        return item == Items.WOODEN_SPEAR || item == Items.STONE_SPEAR ||
                item == Items.IRON_SPEAR || item == Items.COPPER_SPEAR ||
                item == Items.GOLDEN_SPEAR || item == Items.DIAMOND_SPEAR ||
                item == Items.NETHERITE_SPEAR;
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