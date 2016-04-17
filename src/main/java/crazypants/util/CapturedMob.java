package crazypants.util;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockWall;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.enderio.core.common.util.DyeColor;
import com.enderio.core.common.util.EntityUtil;

import crazypants.enderio.EnderIO;
import crazypants.enderio.config.Config;

public class CapturedMob {

  public static final String ENTITY_KEY = "entity";
  public static final String ENTITY_ID_KEY = "entityId";
  public static final String CUSTOM_NAME_KEY = "customName";
  public static final String IS_STUB_KEY = "isStub";
  public static final String IS_VARIANT_KEY = "isVariant";

  private final static List<String> blacklist = new ArrayList<String>();

  private final NBTTagCompound entityNbt;
  private final String entityId;
  private final String customName;
  private final boolean isStub, isVariant;

  private CapturedMob(@Nonnull EntityLivingBase entity) {

    entityId = EntityList.getEntityString(entity);

    entityNbt = entity.serializeNBT();

    String name = null;
    if (entity instanceof EntityLiving) {
      EntityLiving entLiv = (EntityLiving) entity;
      if (entLiv.hasCustomName()) {
        name = entLiv.getCustomNameTag();
      }
    }
    if (name != null && name.length() > 0) {
      customName = name;
    } else {
      customName = null;
    }
    if (entity instanceof EntitySkeleton) {
      isVariant = ((EntitySkeleton) entity).getSkeletonType() == 1;
    } else {
      isVariant = false;
    }

    isStub = false;
  }

  private CapturedMob(NBTTagCompound nbt) {
    if (nbt.hasKey(ENTITY_KEY)) {
      entityNbt = (NBTTagCompound) nbt.getCompoundTag(ENTITY_KEY).copy();
    } else {
      entityNbt = null;
    }
    if (nbt.hasKey(ENTITY_ID_KEY)) {
      entityId = nbt.getString(ENTITY_ID_KEY);
    } else {
      entityId = null;
    }
    if (nbt.hasKey(CUSTOM_NAME_KEY)) {
      customName = nbt.getString(CUSTOM_NAME_KEY);
    } else {
      customName = null;
    }
    isStub = nbt.getBoolean(IS_STUB_KEY);
    isVariant = nbt.getBoolean(IS_VARIANT_KEY);
  }

  private CapturedMob(String entityId, boolean isVariant) {
    this.entityNbt = null;
    this.entityId = entityId;
    this.customName = null;
    this.isStub = true;
    this.isVariant = isVariant;
  }

  public static @Nullable CapturedMob create(@Nullable Entity entity) {
    if (!(entity instanceof EntityLivingBase) || entity.worldObj == null || entity.worldObj.isRemote || entity instanceof EntityPlayer || isBlacklisted(entity)) {
      return null;
    }
    return new CapturedMob((EntityLivingBase) entity);
  }

  public static @Nullable CapturedMob create(@Nullable String entityId, boolean isVariant) {
    if (entityId == null || !EntityList.isStringValidEntityName(entityId)) {
      return null;
    }
    return new CapturedMob(entityId, isVariant);
  }

  public @Nonnull ItemStack toStack(Item item, int meta, int amount) {
    ItemStack stack = new ItemStack(item, amount, meta);
    stack.setTagCompound(toNbt(null));
    if (item == EnderIO.itemSoulVessel && customName == null && "Pig".equals(entityId) && Math.random() < 0.01) {
      stack.getTagCompound().setString(CUSTOM_NAME_KEY, EnderIO.lang.localize("easteregg.piginabottle"));
    }
    return stack;
  }

  public @Nonnull NBTTagCompound toNbt(@Nullable NBTTagCompound nbt) {
    NBTTagCompound data = nbt != null ? nbt : new NBTTagCompound();
    if (entityNbt != null) {
      data.setTag(ENTITY_KEY, entityNbt.copy());
    }
    if (entityId != null) {
      data.setString(ENTITY_ID_KEY, entityId);
    }
    if (customName != null) {
      data.setString(CUSTOM_NAME_KEY, customName);
    }
    if (isStub) {
      data.setBoolean(IS_STUB_KEY, isStub);
    }
    if (isVariant) {
      data.setBoolean(IS_VARIANT_KEY, isVariant);
    }
    return data;
  }

  public static boolean containsSoul(@Nullable NBTTagCompound nbt) {
    return nbt != null && (nbt.hasKey(ENTITY_KEY) || (nbt.hasKey(ENTITY_ID_KEY) && nbt.hasKey(IS_STUB_KEY)));
  }

  public static boolean containsSoul(@Nullable ItemStack stack) {
    return stack != null && stack.hasTagCompound() && containsSoul(stack.getTagCompound());
  }

  public static @Nullable CapturedMob create(@Nullable ItemStack stack) {
    if (containsSoul(stack)) {
      return new CapturedMob(stack.getTagCompound());
    } else {
      return null;
    }
  }

  public static @Nullable CapturedMob create(@Nullable NBTTagCompound nbt) {
    if (containsSoul(nbt)) {
      return new CapturedMob(nbt);
    } else {
      return null;
    }
  }

  public static boolean isBlacklisted(@Nonnull Entity entity) {
    String entityId = EntityList.getEntityString(entity);
    if (entityId == null || entityId.trim().isEmpty() || (!Config.soulVesselCapturesBosses && entity instanceof IBossDisplayData)) {
      return true;
    }
    return Config.soulVesselBlackList.contains(entityId) || blacklist.contains(entityId);
  }

  public boolean spawn(@Nullable World world, @Nullable BlockPos pos, @Nullable EnumFacing side, boolean clone) {
    if (world == null || pos == null) {
      return false;
    }
    @Nonnull
    EnumFacing theSide = side != null ? side : EnumFacing.UP;
    Entity entity = getEntity(world, clone);
    if (entity == null) {
      return false;
    }

    Block blk = world.getBlockState(pos).getBlock();
    double spawnX = pos.getX() + theSide.getFrontOffsetX() + 0.5;
    double spawnY = pos.getY() + theSide.getFrontOffsetY();
    double spawnZ = pos.getZ() + theSide.getFrontOffsetZ() + 0.5;
    if (theSide == EnumFacing.UP && (blk instanceof BlockFence || blk instanceof BlockWall || blk instanceof BlockFenceGate)) {
      spawnY += 0.5;
    }
    entity.setLocationAndAngles(spawnX, spawnY, spawnZ, world.rand.nextFloat() * 360.0F, 0);

    if (!world.checkNoEntityCollision(entity.getEntityBoundingBox()) || !world.getCollidingBoundingBoxes(entity, entity.getEntityBoundingBox()).isEmpty()) {
      return false;
    }

    if (customName != null && entity instanceof EntityLiving) {
      ((EntityLiving) entity).setCustomNameTag(customName);
    }

    if (!world.spawnEntityInWorld(entity)) {
      return false;
    }

    if (entity instanceof EntityLiving) {
      ((EntityLiving) entity).playLivingSound();
    }

    Entity riddenByEntity = entity.riddenByEntity;
    while (riddenByEntity != null) {
      riddenByEntity.setLocationAndAngles(spawnX, spawnY, spawnZ, world.rand.nextFloat() * 360.0F, 0.0F);
      if (world.spawnEntityInWorld(riddenByEntity)) {
        if (riddenByEntity instanceof EntityLiving) {
          ((EntityLiving) riddenByEntity).playLivingSound();
        }
        riddenByEntity = riddenByEntity.riddenByEntity;
      } else {
        riddenByEntity = null;
      }
    }

    return true;
  }

  public @Nullable Entity getEntity(@Nullable World world, boolean clone) {
    Entity entity = null;
    if (world != null) {
      if ((isStub || !clone) && entityId != null) {
        entity = EntityList.createEntityByName(entityId, world);
      } else if (entityNbt != null) {
        if (clone) {
          entity = EntityList.createEntityFromNBT(entityNbt, world);
        } else {
          entity = EntityList.createEntityByName(entityNbt.getString("id"), world);
        }
      }
    }
    if (isVariant && entity instanceof EntitySkeleton) {
      ((EntitySkeleton) entity).setSkeletonType(1);
    }
    return entity;
  }

  public @Nonnull String getDisplayName() {
    String baseName = null;
    if (isVariant && "Skeleton".equals(entityId)) {
      baseName = StatCollector.translateToLocal("entity.witherSkeleton.name");
    } else if (entityId != null) {
      baseName = EntityUtil.getDisplayNameForEntity(entityId);
    } else if (entityNbt != null) {
      baseName = EntityUtil.getDisplayNameForEntity(entityNbt.getString("id"));
    }
    if (baseName == null || baseName.trim().isEmpty()) {
      if (customName != null) {
        return customName;
      } else {
        return "???";
      }
    } else {
      if (customName != null) {
        return customName + " (" + baseName + ")";
      } else {
        return baseName;
      }
    }
  }

  public float getHealth() {
    if (entityNbt != null && entityNbt.hasKey("HealF")) {
      return entityNbt.getFloat("HealF");
    } else {
      return Float.NaN;
    }
  }

  public float getMaxHealth() {
    NBTTagCompound maxHealthAttrib = getAttribute("generic.maxHealth");
    if (maxHealthAttrib != null && maxHealthAttrib.hasKey("Base")) {
      return maxHealthAttrib.getFloat("Base");
    }
    return Float.NaN;
  }

  public @Nullable NBTTagCompound getAttribute(@Nullable String name) {
    if (name != null && entityNbt != null && entityNbt.hasKey("Attributes")) {
      NBTBase tag = entityNbt.getTag("Attributes");
      if (tag instanceof NBTTagList) {
        NBTTagList attributes = (NBTTagList) tag;
        for (int i = 0; i < attributes.tagCount(); i++) {
          NBTTagCompound attrib = attributes.getCompoundTagAt(i);
          if (attrib.hasKey("Name") && name.equals(attrib.getString("Name"))) {
            return attrib;
          }
        }
      }
    }
    return null;
  }

  public @Nullable DyeColor getColor() {
    if (entityNbt != null && entityNbt.hasKey("Color")) {
      int colorIdx = entityNbt.getInteger("Color");
      if (colorIdx >= 0 && colorIdx <= 15) {
        return DyeColor.values()[15 - colorIdx];
      }
    }
    return null;
  }

  public @Nullable String getFluidName() {
    if (entityNbt != null && entityNbt.hasKey("FluidName")) {
      return entityNbt.getString("FluidName");
    }
    return null;
  }

  public static void addToBlackList(String entityName) {
    blacklist.add(entityName);
  }

  public @Nullable String getEntityName() {
    return entityId != null ? entityId : entityNbt != null ? entityNbt.getString("id") : null;
  }

  public boolean isSameType(Entity entity) {
    return entity != null && EntityList.getEntityString(entity) != null && EntityList.getEntityString(entity).equals(getEntityName())
        && (!(entity instanceof EntitySkeleton) || ((EntitySkeleton) entity).getSkeletonType() == (isVariant ? 1 : 0));
  }

  @Override
  public String toString() {
    return "CapturedMob [" + (entityId != null ? "entityId=" + entityId + ", " : "") + (customName != null ? "customName=" + customName + ", " : "")
        + "isStub=" + isStub + ", isVariant=" + isVariant + ", " + (entityNbt != null ? "entityNbt=" + entityNbt + ", " : "") + "getDisplayName()="
        + getDisplayName() + ", getHealth()=" + getHealth() + ", getMaxHealth()=" + getMaxHealth() + ", "
        + (getColor() != null ? "getColor()=" + getColor() + ", " : "") + (getFluidName() != null ? "getFluidName()=" + getFluidName() : "") + "]";
  }

}