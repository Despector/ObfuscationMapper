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

import org.spongepowered.despector.ast.members.MethodEntry;
import org.spongepowered.despector.ast.members.insn.InstructionVisitor;
import org.spongepowered.despector.ast.members.insn.arg.cst.StringConstant;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.MergeOperation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MatchStringConstants implements MergeOperation {

    @Override
    public void operate(MergeEngine set) {
        Walker walker = new Walker();
        for (TypeEntry type : set.getOldSourceSet().getAllClasses()) {
            walker.setType(type);
            for (MethodEntry mth : type.getMethods()) {
                mth.getInstructions().accept(walker);
            }
            for (MethodEntry mth : type.getStaticMethods()) {
                mth.getInstructions().accept(walker);
            }
        }
        Map<String, TypeEntry> old_unique = walker.getUniqueStringConstants();
        walker = new Walker();
        for (TypeEntry type : set.getNewSourceSet().getAllClasses()) {
            walker.setType(type);
            for (MethodEntry mth : type.getMethods()) {
                mth.getInstructions().accept(walker);
            }
            for (MethodEntry mth : type.getStaticMethods()) {
                mth.getInstructions().accept(walker);
            }
        }
        Map<String, TypeEntry> new_unique = walker.getUniqueStringConstants();
        walker = null;
        Map<TypeEntry, TypeEntry> matches = new HashMap<>();
        Set<TypeEntry> no_match = new HashSet<>();
        for (Map.Entry<String, TypeEntry> e : old_unique.entrySet()) {
            if (no_match.contains(e.getValue())) {
                continue;
            }
            TypeEntry n = new_unique.get(e.getKey());
            if (n != null) {
                TypeEntry prev = matches.get(e.getValue());
                if (prev == null) {
                    matches.put(e.getValue(), n);
                }
                if (prev != n) {
                    no_match.add(e.getValue());
                    matches.remove(e.getValue());
                }
            }
        }

        for (Map.Entry<TypeEntry, TypeEntry> m : matches.entrySet()) {
            set.match(m.getKey(), m.getValue());
        }

    }

    private static class Walker extends InstructionVisitor {

        private final Map<String, TypeEntry> unique = new HashMap<>();
        private final Set<String> non_unique = new HashSet<>();
        private TypeEntry current;

        public Walker() {

        }

        public Map<String, TypeEntry> getUniqueStringConstants() {
            return this.unique;
        }

        public void setType(TypeEntry cur) {
            this.current = cur;
        }

        @Override
        public void visitStringConstant(StringConstant cst) {
            if (this.non_unique.contains(cst.getConstant())) {
                return;
            }
            TypeEntry type = this.unique.get(cst.getConstant());
            if (type == null) {
                this.unique.put(cst.getConstant(), this.current);
                return;
            }
            if (type != this.current) {
                this.non_unique.add(cst.getConstant());
                this.unique.remove(cst.getConstant());
            }
        }

    }

}
