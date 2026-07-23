package nl.orbinuity.dispenserspear;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DispenseSpearBehavior implements DispenseItemBehavior {
    private static final Map<String, Float> spearDamage = Map.of(
            "minecraft:wooden_spear", 3f,
            "minecraft:stone_spear", 4f,
            "minecraft:copper_spear", 4f,
            "minecraft:iron_spear", 5f,
            "minecraft:golden_spear", 3f,
            "minecraft:diamond_spear", 6f,
            "minecraft:netherite_spear", 7f
    );
    private static final net.minecraft.core.dispenser.DefaultDispenseItemBehavior DEFAULT_BEHAVIOR =
            new net.minecraft.core.dispenser.DefaultDispenseItemBehavior();

    @Override
    public @NonNull ItemStack dispense(BlockSource source, @NonNull ItemStack stack) {
        Level level = source.level();
        if (level.isClientSide()) return stack;

        Direction direction = source.state().getValue(DispenserBlock.FACING);
        BlockPos sourcePos = source.pos();
        BlockPos targetPos = sourcePos.relative(direction);

        List<Display.ItemDisplay> existingSpears = level.getEntitiesOfClass(
                Display.ItemDisplay.class,
                new AABB(targetPos).inflate(0.5),
                entity -> entity.getTags().contains(DispenserSpearHelper.dispensedSpearId) && entity.getTags().contains("origin:" + sourcePos.getX() + "," + sourcePos.getY() + "," + sourcePos.getZ())
        );

        List<LivingEntity> damageEntities = level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(targetPos).inflate(0.5)
        );

        if (!existingSpears.isEmpty()) {
            Display.ItemDisplay existingSpear = existingSpears.getFirst();

            ItemStack storedItemStack = Objects.requireNonNull(existingSpear.getSlot(0)).get();

            if (storedItemStack.isEmpty()) {
                storedItemStack = stack;
            }

            for (Display.ItemDisplay spear : existingSpears) {
                spear.discard();
            }

            if (stack.getCount() < stack.getMaxStackSize() && DispenserSpearHelper.isSpear(stack.getItem())) {
                stack.grow(1);
            } else {
                ItemStack refundStack = storedItemStack.copy();
                BlockEntity be = source.blockEntity();

                Direction side = Direction.UP;

                if (be.getLevel() != null) {
                    Storage<ItemVariant> handler = ItemStorage.SIDED.find(
                            be.getLevel(),
                            be.getBlockPos(),
                            be.getBlockState(),
                            be,
                            side
                    );

                    if (handler != null) {
                        long insertedAmount;

                        try (Transaction transaction = Transaction.openOuter()) {
                            insertedAmount = handler.insert(
                                    ItemVariant.of(refundStack),
                                    refundStack.getCount(),
                                    transaction
                            );
                            transaction.commit();
                        }

                        ItemStack remainder = refundStack.copy();
                        remainder.shrink((int) insertedAmount);

                        if (!remainder.isEmpty()) {
                            ItemEntity drop = new ItemEntity(
                                    level,
                                    sourcePos.getX() + 0.5,
                                    sourcePos.getY() + 0.5,
                                    sourcePos.getZ() + 0.5,
                                    remainder
                            );
                            drop.setDefaultPickUpDelay();

                            level.addFreshEntity(drop);
                        }
                    }
                }
            }
            return stack;
        }

        if (DispenserSpearHelper.isSpear(stack.getItem())) {
            double x = targetPos.getX() + 0.5;
            double y = targetPos.getY() + 0.5;
            double z = targetPos.getZ() + 0.5;

            ItemStack item = stack.copy();
            item.setCount(1);

            Display.ItemDisplay spearStand = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);

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
            rootNbt.putString("CustomName", stack.getHoverName().getString());

            spearStand.load(TagValueInput.create(new ProblemReporter.Collector(), level.registryAccess(), rootNbt));

            spearStand.setPos(x, y, z);
            spearStand.addTag(DispenserSpearHelper.dispensedSpearId);

            String cleanOrigin = "origin:" + sourcePos.getX() + "," + sourcePos.getY() + "," + sourcePos.getZ();
            spearStand.addTag(cleanOrigin);

            level.addFreshEntity(spearStand);

            for (LivingEntity damageEntity : damageEntities) {
                String itemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(stack.getItem())).toString();
                damageEntity.hurt(level.damageSources().trident(spearStand, null), spearDamage.get(itemId));
            }

            stack.shrink(1);
            return stack;
        } else {
            return DEFAULT_BEHAVIOR.dispense(source, stack);
        }
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

    private ListTag transformList(float[] transform) {
        ListTag transformation = new ListTag();
        for (float val : transform) {
            transformation.add(FloatTag.valueOf(val));
        }
        return transformation;
    }
}