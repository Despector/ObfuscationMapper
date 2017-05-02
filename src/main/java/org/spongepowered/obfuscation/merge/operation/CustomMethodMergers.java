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

import org.spongepowered.despector.ast.insn.Instruction;
import org.spongepowered.despector.ast.insn.cst.StringConstant;
import org.spongepowered.despector.ast.insn.misc.Cast;
import org.spongepowered.despector.ast.stmt.Statement;
import org.spongepowered.despector.ast.stmt.assign.StaticFieldAssignment;
import org.spongepowered.despector.ast.stmt.invoke.InstanceMethodInvoke;
import org.spongepowered.despector.ast.stmt.invoke.New;
import org.spongepowered.despector.ast.stmt.invoke.StaticMethodInvoke;
import org.spongepowered.despector.ast.type.FieldEntry;
import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.obfuscation.data.MappingsSet;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.MergeOperation;
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
            String key = old_set.mapTypeSafe(old.getOwnerName()) + old_set.mapMethodSafe(old.getOwnerName(), old.getName(), old.getDescription())
                    + MappingsSet.MethodMapping.mapSig(old.getDescription(), old_set);
            BiConsumer<MethodMatchEntry, MergeEngine> merger = custom_mergers.get(key);
            if (merger != null) {
                merger.accept(match, set);
                match.setMerged();
            }
        }

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
                TypeEntry mth_owner = set.getOldSourceSet().get(invoke.getOwnerType());
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
                TypeEntry mth_owner = set.getOldSourceSet().get(invoke.getOwnerType());
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

    static {
        custom_mergers.put("net/minecraft/init/SoundEvents<clinit>()V", CustomMethodMergers::bootstrap_handler);
        custom_mergers.put("net/minecraft/init/Blocks<clinit>()V", CustomMethodMergers::bootstrap_handler);
        custom_mergers.put("net/minecraft/init/Items<clinit>()V", CustomMethodMergers::bootstrap_handler);
        custom_mergers.put("net/minecraft/init/Biomes<clinit>()V", CustomMethodMergers::bootstrap_handler);
        custom_mergers.put("net/minecraft/init/MobEffects<clinit>()V", CustomMethodMergers::bootstrap_handler);
        custom_mergers.put("net/minecraft/init/PotionTypes<clinit>()V", CustomMethodMergers::bootstrap_handler);
        custom_mergers.put("net/minecraft/init/Enchantments<clinit>()V", CustomMethodMergers::bootstrap_handler);
        custom_mergers.put("net/minecraft/world/storage/loot/LootTableList<clinit>()V", CustomMethodMergers::bootstrap_handler);

        custom_mergers.put("net/minecraft/stats/StatList<clinit>()V", CustomMethodMergers::statlist_handler);
    }

}
