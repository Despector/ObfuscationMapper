/*
 * The MIT License (MIT)
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.obfuscation.merge.operation;

import org.spongepowered.despector.ast.SourceSet;
import org.spongepowered.despector.ast.insn.Instruction;
import org.spongepowered.despector.ast.insn.cst.StringConstant;
import org.spongepowered.despector.ast.insn.misc.Cast;
import org.spongepowered.despector.ast.insn.var.LocalAccess;
import org.spongepowered.despector.ast.insn.var.StaticFieldAccess;
import org.spongepowered.despector.ast.stmt.Statement;
import org.spongepowered.despector.ast.stmt.StatementBlock;
import org.spongepowered.despector.ast.stmt.assign.LocalAssignment;
import org.spongepowered.despector.ast.stmt.assign.StaticFieldAssignment;
import org.spongepowered.despector.ast.stmt.branch.For;
import org.spongepowered.despector.ast.stmt.invoke.InstanceMethodInvoke;
import org.spongepowered.despector.ast.stmt.invoke.InvokeStatement;
import org.spongepowered.despector.ast.stmt.invoke.New;
import org.spongepowered.despector.ast.stmt.invoke.StaticMethodInvoke;
import org.spongepowered.despector.ast.type.FieldEntry;
import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.despector.util.TypeHelper;
import org.spongepowered.obfuscation.data.MappingsSet;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.MergeOperation;
import org.spongepowered.obfuscation.merge.data.FieldMatchEntry;
import org.spongepowered.obfuscation.merge.data.MethodMatchEntry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class CustomMethodMergers implements MergeOperation {

    private static final Map<String, BiConsumer<MethodMatchEntry, MergeEngine>> custom_mergers = new HashMap<>();

    @Override
    public void operate(MergeEngine set) {

        for (MethodMatchEntry match : set.getAllMethodMatches()) {
            if (match.getNewMethod() == null || match.isMerged()) {
                continue;
            }
            MethodEntry old = match.getOldMethod();
            MappingsSet old_set = set.getOldMappings();
            String key = "L" + old_set.mapTypeSafe(old.getOwnerName()) + ";"
                    + old_set.mapMethodSafe(old.getOwnerName(), old.getName(), old.getDescription())
                    + MappingsSet.MethodMapping.mapSig(old.getDescription(), old_set);
            BiConsumer<MethodMatchEntry, MergeEngine> merger = custom_mergers.get(key);
            if (merger != null) {
                merger.accept(match, set);
                match.setMerged();
            }
        }

    }

    private static void noop(MethodMatchEntry match, MergeEngine set) {
    }

    private static void bootstrap_handler(MethodMatchEntry match, MergeEngine set) {

        if (match.getOldMethod().getInstructions() == null || match.getNewMethod().getInstructions() == null) {
            return;
        }
        MethodEntry old_reg = null;
        Map<String, FieldEntry> old_sounds = new HashMap<>();
        for (Statement stmt : match.getOldMethod().getInstructions()) {
            if (stmt instanceof StaticFieldAssignment) {
                StaticFieldAssignment assign = (StaticFieldAssignment) stmt;
                TypeEntry field_owner = set.getOldSourceSet().get(assign.getOwnerName());
                if (field_owner == null) {
                    continue;
                }
                FieldEntry fld = field_owner.getStaticField(assign.getFieldName());
                if (fld == null) {
                    continue;
                }
                Instruction val = assign.getValue();
                if (val instanceof Cast) {
                    val = ((Cast) val).getValue();
                }
                if (!(val instanceof StaticMethodInvoke)) {
                    continue;
                }
                StaticMethodInvoke invoke = (StaticMethodInvoke) val;
                TypeEntry mth_owner = set.getOldSourceSet().get(invoke.getOwnerName());
                if (mth_owner != null) {
                    MethodEntry reg = mth_owner.getStaticMethod(invoke.getMethodName(), invoke.getMethodDescription());
                    if (reg != null) {
                        old_reg = reg;
                    }
                }
                if (invoke.getParameters().length != 1 || !(invoke.getParameters()[0] instanceof StringConstant)) {
                    continue;
                }
                old_sounds.put(((StringConstant) invoke.getParameters()[0]).getConstant(), fld);
            }
        }
        for (Statement stmt : match.getNewMethod().getInstructions()) {
            if (stmt instanceof StaticFieldAssignment) {
                StaticFieldAssignment assign = (StaticFieldAssignment) stmt;
                TypeEntry field_owner = set.getNewSourceSet().get(assign.getOwnerName());
                if (field_owner == null) {
                    continue;
                }
                FieldEntry fld = field_owner.getStaticField(assign.getFieldName());
                if (fld == null) {
                    continue;
                }
                Instruction val = assign.getValue();
                if (val instanceof Cast) {
                    val = ((Cast) val).getValue();
                }
                if (!(val instanceof StaticMethodInvoke)) {
                    continue;
                }
                StaticMethodInvoke invoke = (StaticMethodInvoke) val;
                TypeEntry mth_owner = set.getOldSourceSet().get(invoke.getOwnerName());
                if (mth_owner != null) {
                    MethodEntry reg = mth_owner.getStaticMethod(invoke.getMethodName(), invoke.getMethodDescription());
                    if (reg != null) {
                        set.vote(old_reg, reg);
                    }
                }
                if (invoke.getParameters().length != 1 || !(invoke.getParameters()[0] instanceof StringConstant)) {
                    continue;
                }
                String key = ((StringConstant) invoke.getParameters()[0]).getConstant();
                FieldEntry old = old_sounds.get(key);
                if (old != null) {
                    set.vote(old, fld);
                }
            }
        }
    }

    private static void statlist_handler(MethodMatchEntry match, MergeEngine set) {
        if (match.getOldMethod().getInstructions() == null || match.getNewMethod().getInstructions() == null) {
            return;
        }
        Map<String, FieldEntry> old_fields = new HashMap<>();
        Map<String, Instruction> old_vals = new HashMap<>();
        for (Statement stmt : match.getOldMethod().getInstructions()) {
            if (stmt instanceof StaticFieldAssignment) {
                StaticFieldAssignment assign = (StaticFieldAssignment) stmt;
                TypeEntry field_owner = set.getOldSourceSet().get(assign.getOwnerName());
                if (field_owner == null) {
                    continue;
                }
                FieldEntry fld = field_owner.getStaticField(assign.getFieldName());
                if (fld == null) {
                    continue;
                }
                Instruction val = assign.getValue();
                if (val instanceof Cast) {
                    val = ((Cast) val).getValue();
                }
                String key = null;
                while (val instanceof InstanceMethodInvoke) {
                    val = ((InstanceMethodInvoke) val).getCallee();
                }
                if (!(val instanceof New)) {
                    continue;
                }
                New invoke = (New) val;
                if (invoke.getParameters().length == 0 || !(invoke.getParameters()[0] instanceof StringConstant)) {
                    continue;
                }
                key = ((StringConstant) invoke.getParameters()[0]).getConstant();
                old_fields.put(key, fld);
                old_vals.put(key, val);
            }
        }
        for (Statement stmt : match.getNewMethod().getInstructions()) {
            if (stmt instanceof StaticFieldAssignment) {
                StaticFieldAssignment assign = (StaticFieldAssignment) stmt;
                TypeEntry field_owner = set.getNewSourceSet().get(assign.getOwnerName());
                if (field_owner == null) {
                    continue;
                }
                FieldEntry fld = field_owner.getStaticField(assign.getFieldName());
                if (fld == null) {
                    continue;
                }
                Instruction val = assign.getValue();
                if (val instanceof Cast) {
                    val = ((Cast) val).getValue();
                }
                String key = null;
                while (val instanceof InstanceMethodInvoke) {
                    val = ((InstanceMethodInvoke) val).getCallee();
                }
                if (!(val instanceof New)) {
                    continue;
                }
                New invoke = (New) val;
                if (invoke.getParameters().length == 0 || !(invoke.getParameters()[0] instanceof StringConstant)) {
                    continue;
                }
                key = ((StringConstant) invoke.getParameters()[0]).getConstant();
                FieldEntry old = old_fields.get(key);
                if (old != null) {
                    set.vote(old, fld);
                    MergeUtil.merge(set, old_vals.get(key), assign.getValue());
                }
            }
        }
    }

    private static void register_items(MethodMatchEntry match, MergeEngine set) {
        if (match.getOldMethod().getInstructions() == null || match.getNewMethod().getInstructions() == null) {
            return;
        }
        Map<String, TypeEntry> old_types = new HashMap<>();
        Map<String, TypeEntry> new_types = new HashMap<>();
        findBlockTypes(match.getOldMethod().getInstructions(), set.getOldSourceSet(), old_types);
        findBlockItems(match.getOldMethod().getInstructions(), set.getOldSourceSet(), old_types);
        findBlockTypes(match.getNewMethod().getInstructions(), set.getNewSourceSet(), new_types);
        findBlockItems(match.getNewMethod().getInstructions(), set.getNewSourceSet(), new_types);
        for (Map.Entry<String, TypeEntry> e : new_types.entrySet()) {
            TypeEntry old = old_types.get(e.getKey());
            if (old != null) {
                set.vote(old, e.getValue());
            }
        }
    }

    private static String findBootstrapKey(TypeEntry owner, FieldEntry key) {

        MethodEntry clinit = owner.getStaticMethod("<clinit>");

        for (Statement stmt : clinit.getInstructions()) {
            if (stmt instanceof StaticFieldAssignment) {
                StaticFieldAssignment assign = (StaticFieldAssignment) stmt;
                if (assign.getFieldName().equals(key.getName())) {
                    Instruction val = assign.getValue();
                    if (val instanceof Cast) {
                        val = ((Cast) val).getValue();
                    }
                    if (!(val instanceof StaticMethodInvoke)) {
                        continue;
                    }
                    StaticMethodInvoke invoke = (StaticMethodInvoke) val;
                    if (invoke.getParameters().length != 1 || !(invoke.getParameters()[0] instanceof StringConstant)) {
                        continue;
                    }
                    return ((StringConstant) invoke.getParameters()[0]).getConstant();
                }
            }
        }

        return null;
    }

    private static void findBlockItems(StatementBlock block, SourceSet src, Map<String, TypeEntry> types) {
        for (Statement stmt : block) {
            if (stmt instanceof InvokeStatement) {
                Instruction inner = ((InvokeStatement) stmt).getInstruction();
                if (inner instanceof StaticMethodInvoke) {
                    StaticMethodInvoke reg = (StaticMethodInvoke) inner;
                    if (reg.getParameters().length != 2) {
                        continue;
                    }
                    Instruction val = reg.getParameters()[1];
                    if (val instanceof New) {
                        Instruction key_insn = reg.getParameters()[0];
                        if (!(key_insn instanceof StaticFieldAccess)) {
                            continue;
                        }
                        StaticFieldAccess key_acc = (StaticFieldAccess) key_insn;
                        TypeEntry key_owner = src.get(key_acc.getOwnerName());
                        FieldEntry key = key_owner.getStaticField(key_acc.getFieldName());
                        String type = TypeHelper.descToType(((New) val).getType().getDescriptor());
                        TypeEntry block_type = src.get(type);
                        if (block_type != null) {
                            String bkey = findBootstrapKey(key_owner, key);
                            types.put(bkey, block_type);
                        }
                        continue;
                    }
                    if (val instanceof Cast) {
                        val = ((Cast) val).getValue();
                    }
                    String key = null;
                    if (val instanceof InstanceMethodInvoke) {
                        InstanceMethodInvoke invoke = (InstanceMethodInvoke) val;
                        if (!invoke.getMethodDescription().startsWith("(Ljava/lang/String;)")
                                || !(invoke.getParameters()[0] instanceof StringConstant)) {
                            continue;
                        }
                        key = ((StringConstant) invoke.getParameters()[0]).getConstant();
                    } else {
                        continue;
                    }
                    while (val instanceof InstanceMethodInvoke) {
                        val = ((InstanceMethodInvoke) val).getCallee();
                    }
                    if (!(val instanceof New)) {
                        continue;
                    }
                    String type = TypeHelper.descToType(((New) val).getType().getDescriptor());
                    TypeEntry block_type = src.get(type);
                    if (block_type != null) {
                        types.put(key, block_type);
                    }
                }
            }
        }
    }

    private static void register_blocks(MethodMatchEntry match, MergeEngine set) {
        if (match.getOldMethod().getInstructions() == null || match.getNewMethod().getInstructions() == null) {
            return;
        }
        Map<String, TypeEntry> old_types = new HashMap<>();
        Map<String, TypeEntry> new_types = new HashMap<>();
        findBlockTypes(match.getOldMethod().getInstructions(), set.getOldSourceSet(), old_types);
        findBlockTypes(match.getNewMethod().getInstructions(), set.getNewSourceSet(), new_types);
        for (Map.Entry<String, TypeEntry> e : new_types.entrySet()) {
            TypeEntry old = old_types.get(e.getKey());
            if (old != null) {
                set.vote(old, e.getValue());
            }
        }
    }

    private static void findBlockTypes(StatementBlock block, SourceSet src, Map<String, TypeEntry> types) {
        Instruction last = null;
        for (Statement stmt : block) {
            if (stmt instanceof For) {
                break;
            }
            if (stmt instanceof LocalAssignment) {
                last = ((LocalAssignment) stmt).getValue();
            } else if (stmt instanceof InvokeStatement) {
                Instruction inner = ((InvokeStatement) stmt).getInstruction();
                if (inner instanceof StaticMethodInvoke) {
                    StaticMethodInvoke reg = (StaticMethodInvoke) inner;
                    if (reg.getParameters().length != 3) {
                        last = null;
                        continue;
                    }
                    if (!reg.getMethodDescription().startsWith("(I")) {
                        last = null;
                        continue;
                    }
                    Instruction val = reg.getParameters()[2];
                    if (val instanceof Cast) {
                        val = ((Cast) val).getValue();
                    }
                    String key = null;
                    if (!(reg.getParameters()[1] instanceof StringConstant)) {
                        if (val instanceof InstanceMethodInvoke) {
                            InstanceMethodInvoke invoke = (InstanceMethodInvoke) val;
                            if (!invoke.getMethodDescription().startsWith("(Ljava/lang/String;)")
                                    || !(invoke.getParameters()[0] instanceof StringConstant)) {
                                last = null;
                                continue;
                            }
                            key = ((StringConstant) invoke.getParameters()[0]).getConstant();
                        }
                    } else {
                        key = ((StringConstant) reg.getParameters()[1]).getConstant();
                    }
                    if (key == null) {
                        last = null;
                        continue;
                    }
                    if (val instanceof LocalAccess && last != null) {
                        val = last;
                    }
                    while (val instanceof InstanceMethodInvoke) {
                        val = ((InstanceMethodInvoke) val).getCallee();
                    }
                    if (!(val instanceof New)) {
                        last = null;
                        continue;
                    }
                    String type = TypeHelper.descToType(((New) val).getType().getDescriptor());
                    TypeEntry block_type = src.get(type);
                    if (block_type != null) {
                        types.put(key, block_type);
                    }
                    last = null;
                } else {
                    last = null;
                }
            }
        }
    }

    private static void register_enchantments(MethodMatchEntry match, MergeEngine set) {
        if (match.getOldMethod().getInstructions() == null || match.getNewMethod().getInstructions() == null) {
            return;
        }
        Map<String, TypeEntry> old_types = new HashMap<>();
        Map<String, TypeEntry> new_types = new HashMap<>();
        findEnchantments(match.getOldMethod().getInstructions(), set.getOldSourceSet(), old_types);
        findEnchantments(match.getNewMethod().getInstructions(), set.getNewSourceSet(), new_types);
        for (Map.Entry<String, TypeEntry> e : new_types.entrySet()) {
            TypeEntry old = old_types.get(e.getKey());
            if (old != null) {
                set.vote(old, e.getValue());
            }
        }
    }

    private static void findEnchantments(StatementBlock block, SourceSet src, Map<String, TypeEntry> types) {
        for (Statement stmt : block) {
            if (stmt instanceof InvokeStatement) {
                Instruction inner = ((InvokeStatement) stmt).getInstruction();
                if (inner instanceof InstanceMethodInvoke) {
                    InstanceMethodInvoke reg = (InstanceMethodInvoke) inner;
                    if (reg.getParameters().length != 3) {
                        continue;
                    }
                    Instruction key_val = reg.getParameters()[1];
                    String key = null;
                    if (key_val instanceof New) {
                        New n = (New) key_val;
                        if (n.getParameters().length == 1 && n.getParameters()[0] instanceof StringConstant) {
                            key = ((StringConstant) n.getParameters()[0]).getConstant();
                        }
                    }
                    if (key == null) {
                        continue;
                    }
                    Instruction val = reg.getParameters()[2];
                    if (val instanceof Cast) {
                        val = ((Cast) val).getValue();
                    }
                    while (val instanceof InstanceMethodInvoke) {
                        val = ((InstanceMethodInvoke) val).getCallee();
                    }
                    if (!(val instanceof New)) {
                        continue;
                    }
                    String type = TypeHelper.descToType(((New) val).getType().getDescriptor());
                    TypeEntry block_type = src.get(type);
                    if (block_type != null) {
                        types.put(key, block_type);
                    }
                }
            }
        }
    }

    static {
        custom_mergers.put("Lnet/minecraft/init/SoundEvents;<clinit>()V", CustomMethodMergers::bootstrap_handler);
        custom_mergers.put("Lnet/minecraft/init/Blocks;<clinit>()V", CustomMethodMergers::bootstrap_handler);
        custom_mergers.put("Lnet/minecraft/init/Items;<clinit>()V", CustomMethodMergers::bootstrap_handler);
        custom_mergers.put("Lnet/minecraft/init/Biomes;<clinit>()V", CustomMethodMergers::bootstrap_handler);
        custom_mergers.put("Lnet/minecraft/init/MobEffects;<clinit>()V", CustomMethodMergers::bootstrap_handler);
        custom_mergers.put("Lnet/minecraft/init/PotionTypes;<clinit>()V", CustomMethodMergers::bootstrap_handler);
        custom_mergers.put("Lnet/minecraft/init/Enchantments;<clinit>()V", CustomMethodMergers::bootstrap_handler);
        custom_mergers.put("Lnet/minecraft/world/storage/loot/LootTableList;<clinit>()V", CustomMethodMergers::bootstrap_handler);

        custom_mergers.put("Lnet/minecraft/stats/StatList;<clinit>()V", CustomMethodMergers::statlist_handler);

        custom_mergers.put("Lnet/minecraft/util/datafix/DataFixesManager;func_188276_a(Lnet/minecraft/util/datafix/DataFixer;)V",
                CustomMethodMergers::noop);
        custom_mergers.put("Lnet/minecraft/util/datafix/DataFixesManager;func_188279_a()Lnet/minecraft/util/datafix/DataFixer;",
                CustomMethodMergers::noop);

        custom_mergers.put("Lnet/minecraft/block/Block;func_149671_p()V", CustomMethodMergers::register_blocks);
        custom_mergers.put("Lnet/minecraft/item/Item;func_150900_l()V", CustomMethodMergers::register_items);
        custom_mergers.put("Lnet/minecraft/world/biome/Biome;func_185358_q()V", CustomMethodMergers::register_blocks);
        custom_mergers.put("Lnet/minecraft/enchantment/Enchantment;func_185257_f()V", CustomMethodMergers::register_enchantments);
    }

}
