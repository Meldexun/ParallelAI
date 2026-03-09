package meldexun.parallelai.asm;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.google.common.collect.BiMap;

import meldexun.asmutil2.ASMUtil;
import meldexun.asmutil2.ClassNodeTransformer;
import meldexun.asmutil2.HashMapClassNodeClassTransformer;
import meldexun.asmutil2.IClassTransformerRegistry;
import meldexun.asmutil2.MethodNodeTransformer;
import meldexun.asmutil2.NonLoadingClassWriter;
import meldexun.asmutil2.reader.ClassUtil;
import meldexun.parallelai.ParallelAI.TickStage;
import net.minecraft.entity.Entity;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;

public class ParallelAIClassTransformer extends HashMapClassNodeClassTransformer implements IClassTransformer {

	private static final ClassUtil REMAPPING_CLASS_UTIL;
	static {
		try {
			Class<?> FMLDeobfuscatingRemapper = Class.forName("net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper", true, Launch.classLoader);
			Field _INSTANCE = FMLDeobfuscatingRemapper.getField("INSTANCE");
			Field _classNameBiMap = FMLDeobfuscatingRemapper.getDeclaredField("classNameBiMap");
			_classNameBiMap.setAccessible(true);
			@SuppressWarnings("unchecked")
			BiMap<String, String> deobfuscationMap = (BiMap<String, String>) _classNameBiMap.get(_INSTANCE.get(null));
			REMAPPING_CLASS_UTIL = ClassUtil.getInstance(new ClassUtil.Configuration(Launch.classLoader, deobfuscationMap.inverse(), deobfuscationMap));
		} catch (ReflectiveOperationException e) {
			throw new UnsupportedOperationException(e);
		}
	}

	@Override
	protected void registerTransformers(IClassTransformerRegistry registry) {

	}

	@Override
	protected List<ClassNodeTransformer> getClassNodeTransformers(String className) {
		if (!className.startsWith("net.minecraft.entity.") && !className.startsWith("net.minecraft.client.entity.") || className.equals("net.minecraft.entity.Entity")) {
			return Collections.emptyList();
		}
		return Arrays.asList(
				MethodNodeTransformer.createObf("func_70071_h_", "onUpdate", 0, ClassWriter.COMPUTE_FRAMES, methodNode -> {
					MethodInsnNode superCall;
					if (className.endsWith(".EntityLivingBase")) {
						superCall = ASMUtil.first(methodNode).opcode(Opcodes.INVOKEVIRTUAL).methodInsnObf("func_70636_d", "onLivingUpdate").find();
					} else {
						try {
							superCall = ASMUtil.first(methodNode).methodInsn(methodNode.name).find();
						} catch (NoSuchElementException e) {
							ASMUtil.LOGGER.error("Can't find super.onUpdate in " + className + ".onUpdate, is this a bug or intentional?");
							return;
						}
					}
					
					LabelNode skipPre = new LabelNode();
					methodNode.instructions.insert(ASMUtil.listOf(
							new VarInsnNode(Opcodes.ALOAD, 0),
							new MethodInsnNode(Opcodes.INVOKESTATIC, "meldexun/parallelai/asm/ParallelAIClassTransformer$Hook", "tickPre", "(Lnet/minecraft/entity/Entity;)Z", false),
							new JumpInsnNode(Opcodes.IFEQ, skipPre)
					));
					methodNode.instructions.insertBefore(superCall.getPrevious(), skipPre);
					
					LabelNode skipPost = new LabelNode();
					methodNode.instructions.insert(superCall, ASMUtil.listOf(
							new VarInsnNode(Opcodes.ALOAD, 0),
							new MethodInsnNode(Opcodes.INVOKESTATIC, "meldexun/parallelai/asm/ParallelAIClassTransformer$Hook", "tickPost", "(Lnet/minecraft/entity/Entity;)Z", false),
							new JumpInsnNode(Opcodes.IFEQ, skipPost)
					));
					methodNode.instructions.insertBefore(ASMUtil.last(methodNode).opcode(Opcodes.RETURN).find(), skipPost);
				}),
				MethodNodeTransformer.createObf("func_70636_d", "onLivingUpdate", 0, ClassWriter.COMPUTE_FRAMES, methodNode -> {
					MethodInsnNode superCall;
					if (className.endsWith(".EntityLivingBase")) {
						superCall = ASMUtil.first(methodNode).methodInsnObf("func_70626_be", "updateEntityActionState").find();
//						ASMUtil.replace(methodNode, superCall, new InsnNode(Opcodes.POP));

						AbstractInsnNode preAI = ASMUtil.first(methodNode).ldcInsn("ai").findThenPrev().varInsn(0).find();
						AbstractInsnNode postAI = ASMUtil.first(methodNode).ldcInsn("jump").findThenPrev().varInsn(0).find();
						
						LabelNode skipPre = new LabelNode();
						methodNode.instructions.insert(ASMUtil.listOf(
								new VarInsnNode(Opcodes.ALOAD, 0),
								new MethodInsnNode(Opcodes.INVOKESTATIC, "meldexun/parallelai/asm/ParallelAIClassTransformer$Hook", "tickPre", "(Lnet/minecraft/entity/Entity;)Z", false),
								new JumpInsnNode(Opcodes.IFEQ, skipPre)
						));
						methodNode.instructions.insertBefore(preAI, skipPre);
						
						LabelNode skipPost = new LabelNode();
						methodNode.instructions.insertBefore(postAI, ASMUtil.listOf(
								new VarInsnNode(Opcodes.ALOAD, 0),
								new MethodInsnNode(Opcodes.INVOKESTATIC, "meldexun/parallelai/asm/ParallelAIClassTransformer$Hook", "tickPost", "(Lnet/minecraft/entity/Entity;)Z", false),
								new JumpInsnNode(Opcodes.IFEQ, skipPost)
						));
						methodNode.instructions.insertBefore(ASMUtil.last(methodNode).opcode(Opcodes.RETURN).find(), skipPost);
					} else {
						try {
							superCall = ASMUtil.first(methodNode).methodInsn(methodNode.name).find();
						} catch (NoSuchElementException e) {
							ASMUtil.LOGGER.error("Can't find super.onLivingUpdate in " + className + ".onLivingUpdate, is this a bug or intentional?");
							return;
						}

						LabelNode skipPre = new LabelNode();
						methodNode.instructions.insert(ASMUtil.listOf(
								new VarInsnNode(Opcodes.ALOAD, 0),
								new MethodInsnNode(Opcodes.INVOKESTATIC, "meldexun/parallelai/asm/ParallelAIClassTransformer$Hook", "tickPre", "(Lnet/minecraft/entity/Entity;)Z", false),
								new JumpInsnNode(Opcodes.IFEQ, skipPre)
						));
						methodNode.instructions.insertBefore(superCall.getPrevious(), skipPre);
						
						LabelNode skipPost = new LabelNode();
						methodNode.instructions.insert(superCall, ASMUtil.listOf(
								new VarInsnNode(Opcodes.ALOAD, 0),
								new MethodInsnNode(Opcodes.INVOKESTATIC, "meldexun/parallelai/asm/ParallelAIClassTransformer$Hook", "tickPost", "(Lnet/minecraft/entity/Entity;)Z", false),
								new JumpInsnNode(Opcodes.IFEQ, skipPost)
						));
						methodNode.instructions.insertBefore(ASMUtil.last(methodNode).opcode(Opcodes.RETURN).find(), skipPost);
					}
				}),
				MethodNodeTransformer.createObf("func_70626_be", "updateEntityActionState", 0, ClassWriter.COMPUTE_FRAMES, methodNode -> {
					if (!className.endsWith(".EntityLiving")) {
						return;
					}
					AbstractInsnNode preEnd = ASMUtil.first(methodNode).ldcInsn("targetSelector").findThenPrev().varInsn(0).find();
					AbstractInsnNode postStart = ASMUtil.first(methodNode).ldcInsn("mob tick").findThenPrev().varInsn(0).find();
					
					LabelNode skipPre = new LabelNode();
					LabelNode skipAI = new LabelNode();
					LabelNode skipPost = new LabelNode();
					
					methodNode.instructions.insert(ASMUtil.listOf(
							new VarInsnNode(Opcodes.ALOAD, 0),
							new MethodInsnNode(Opcodes.INVOKESTATIC, "meldexun/parallelai/asm/ParallelAIClassTransformer$Hook", "tickPre", "(Lnet/minecraft/entity/Entity;)Z", false),
							new JumpInsnNode(Opcodes.IFEQ, skipPre)
					));
					methodNode.instructions.insertBefore(preEnd, ASMUtil.listOf(
							skipPre,
							new VarInsnNode(Opcodes.ALOAD, 0),
							new MethodInsnNode(Opcodes.INVOKESTATIC, "meldexun/parallelai/asm/ParallelAIClassTransformer$Hook", "tickAI", "(Lnet/minecraft/entity/Entity;)Z", false),
							new JumpInsnNode(Opcodes.IFEQ, skipAI)
					));
					methodNode.instructions.insertBefore(postStart, ASMUtil.listOf(
							skipAI,
							new VarInsnNode(Opcodes.ALOAD, 0),
							new MethodInsnNode(Opcodes.INVOKESTATIC, "meldexun/parallelai/asm/ParallelAIClassTransformer$Hook", "tickPost", "(Lnet/minecraft/entity/Entity;)Z", false),
							new JumpInsnNode(Opcodes.IFEQ, skipPost)
					));
					methodNode.instructions.insertBefore(ASMUtil.last(methodNode).opcode(Opcodes.RETURN).find(), ASMUtil.listOf(
							skipPost
					));
				}));
	}

	public static class Hook {
		public static boolean tickPre(Entity entity) {
			return ((Supplier<TickStage>) entity.world).get() == TickStage.PRE || ((Supplier<TickStage>) entity.world).get() == TickStage.ALL;
		}

		public static boolean tickAI(Entity entity) {
			return ((Supplier<TickStage>) entity.world).get() == TickStage.AI || ((Supplier<TickStage>) entity.world).get() == TickStage.ALL;
		}

		public static boolean tickPost(Entity entity) {
			return ((Supplier<TickStage>) entity.world).get() == TickStage.POST || ((Supplier<TickStage>) entity.world).get() == TickStage.ALL;
		}
	}

	@Override
	protected ClassWriter createClassWriter(int flags) {
		return new NonLoadingClassWriter(flags, REMAPPING_CLASS_UTIL);
	}

}
