package net.charter.block;

import org.jetbrains.annotations.Nullable;

import net.charter.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * The tablet of Divine Dominance. Once placed it raises a golden square wall that slowly closes in; only its
 * owner and souls bound to Charter can cross it, and whoever is still inside when it closes is judged.
 */
public class DivineDominanceBlock extends BlockWithEntity {
	public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;

	/** Tablet facing north or south: wide along X, thin along Z. */
	private static final VoxelShape SHAPE_Z = Block.createCuboidShape(1.0, 0.0, 6.0, 15.0, 15.0, 10.0);
	/** Tablet facing east or west: wide along Z, thin along X. */
	private static final VoxelShape SHAPE_X = Block.createCuboidShape(6.0, 0.0, 1.0, 10.0, 15.0, 15.0);

	public DivineDominanceBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext context) {
		return this.getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(FACING).getAxis() == Direction.Axis.Z ? SHAPE_Z : SHAPE_X;
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		// BlockWithEntity makes blocks invisible by default
		return BlockRenderType.MODEL;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new DivineDominanceBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient) {
			return null;
		}
		return checkType(type, ModBlockEntities.DIVINE_DOMINANCE, DivineDominanceBlockEntity::tick);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.onPlaced(world, pos, state, placer, stack);
		if (world instanceof ServerWorld) {
			BlockEntity entity = world.getBlockEntity(pos);
			if (entity instanceof DivineDominanceBlockEntity) {
				PlayerEntity owner = placer instanceof PlayerEntity ? (PlayerEntity) placer : null;
				((DivineDominanceBlockEntity) entity).arm((ServerWorld) world, owner);
			}
		}
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		BlockEntity entity = world.getBlockEntity(pos);
		if (entity instanceof DivineDominanceBlockEntity) {
			((DivineDominanceBlockEntity) entity).describe(player);
		}
		return ActionResult.CONSUME;
	}
}
