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
package org.spongepowered.obfuscation.merge;

import org.spongepowered.despector.ast.SourceSet;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.obfuscation.data.MappingsSet;
import org.spongepowered.obfuscation.merge.data.MatchEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class MergeEngine {

    public static MergeOperation jumpTo(int index, Predicate<MergeEngine> condition) {
        return new JumpOperation(index, condition);
    }

    private List<MergeOperation> operations = new ArrayList<>();

    private final Map<String, MatchEntry> matches = new HashMap<>();

    private final SourceSet old_src;
    private final SourceSet new_src;
    private final MappingsSet old_mappings;
    private final MappingsSet new_mappings;

    public MergeEngine(SourceSet oldsrc, MappingsSet oldmap, SourceSet newsrc, MappingsSet newmap) {
        this.old_src = oldsrc;
        this.new_src = newsrc;
        this.old_mappings = oldmap;
        this.new_mappings = newmap;
    }

    public SourceSet getOldSourceSet() {
        return this.old_src;
    }

    public SourceSet getNewSourceSet() {
        return this.new_src;
    }

    public MappingsSet getOldMappings() {
        return this.old_mappings;
    }

    public MappingsSet getNewMappings() {
        return this.new_mappings;
    }

    public MatchEntry getMetch(String name) {
        return this.matches.get(name);
    }

    public MatchEntry match(TypeEntry old, TypeEntry n) {
        MatchEntry entry = this.matches.get(old.getName());
        if (entry != null) {
            if (entry.getNewType() != n) {
                throw new IllegalStateException("Type mismatch error: Tried " + old.getName() + " -> " + n.getName() + " but already matched to "
                        + entry.getNewType().getName());
            }
            return entry;
        }
        entry = new MatchEntry(old, n);
        this.matches.put(old.getName(), entry);
        return entry;
    }

    public void addOperation(int index, MergeOperation op) {
        this.operations.add(index, op);
    }

    public void addOperation(MergeOperation op) {
        this.operations.add(op);
    }

    public List<MergeOperation> getOperations() {
        return this.operations;
    }

    public void merge() {
        for (int i = 0; i < this.operations.size(); i++) {
            MergeOperation op = this.operations.get(i);
            if (op instanceof JumpOperation) {
                JumpOperation jump = (JumpOperation) op;
                if (jump.condition.test(this)) {
                    i = jump.target - 1;
                }
                continue;
            }
            op.operate(this);
        }

        for (MatchEntry entry : this.matches.values()) {
            String mapped = this.old_mappings.mapTypeSafe(entry.getOldType().getName());
            if (mapped != null) {
                this.new_mappings.addTypeMapping(entry.getNewType().getName(), mapped);
            }

            // TODO add field and method mappings here
        }

    }

    private static class JumpOperation implements MergeOperation {

        public int target;
        public Predicate<MergeEngine> condition;

        public JumpOperation(int target, Predicate<MergeEngine> condition) {
            this.target = target;
            this.condition = condition;
        }

        @Override
        public void operate(MergeEngine set) {
        }
    }

}
