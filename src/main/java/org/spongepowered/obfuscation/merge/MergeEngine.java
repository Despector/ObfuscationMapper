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
import org.spongepowered.despector.ast.type.FieldEntry;
import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.obfuscation.data.MappingsSet;
import org.spongepowered.obfuscation.merge.data.FieldMatchEntry;
import org.spongepowered.obfuscation.merge.data.MatchEntry;
import org.spongepowered.obfuscation.merge.data.MethodMatchEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class MergeEngine {

    public static MergeOperation jumpTo(int index, Predicate<MergeEngine> condition) {
        return new JumpOperation(index, condition);
    }

    private List<MergeOperation> operations = new ArrayList<>();

    private final Map<TypeEntry, MatchEntry> matches = new HashMap<>();
    private final Map<MethodEntry, MethodMatchEntry> method_matches = new HashMap<>();
    private final Map<FieldEntry, FieldMatchEntry> field_matches = new HashMap<>();

    private final SourceSet old_src;
    private final SourceSet new_src;
    private final MappingsSet old_mappings;
    private final MappingsSet new_mappings;
    private MappingsSet validation_mappings;

    private int changes_last_cycle = 0;

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

    public MappingsSet getValidationMappings() {
        return this.validation_mappings;
    }

    public void setValidationMappings(MappingsSet mappings) {
        this.validation_mappings = mappings;
    }

    public int getChangesLastCycle() {
        return this.changes_last_cycle;
    }

    public void resetChanges() {
        this.changes_last_cycle = 0;
    }

    public MatchEntry getMatch(TypeEntry t) {
        MatchEntry m = this.matches.get(t);
        if (m == null) {
            m = new MatchEntry(t);
            this.matches.put(t, m);
        }
        return m;
    }

    public void vote(TypeEntry old, TypeEntry n) {
        MatchEntry entry = getMatch(old);
        entry.vote(n);
    }

    public Collection<MatchEntry> getAllMatches() {
        return this.matches.values();
    }

    public MethodMatchEntry getMethodMatch(MethodEntry t) {
        MethodMatchEntry m = this.method_matches.get(t);
        if (m == null) {
            m = new MethodMatchEntry(t);
            this.method_matches.put(t, m);
        }
        return m;
    }

    public void vote(MethodEntry old, MethodEntry n) {
        MethodMatchEntry entry = getMethodMatch(old);
        entry.vote(n);
    }

    public Collection<MethodMatchEntry> getAllMethodMatches() {
        return this.method_matches.values();
    }

    public FieldMatchEntry getFieldMatch(FieldEntry t) {
        FieldMatchEntry m = this.field_matches.get(t);
        if (m == null) {
            m = new FieldMatchEntry(t);
            this.field_matches.put(t, m);
        }
        return m;
    }

    public void vote(FieldEntry old, FieldEntry n) {
        FieldMatchEntry entry = getFieldMatch(old);
        entry.vote(n);
    }

    public Collection<FieldMatchEntry> getAllFieldMatches() {
        return this.field_matches.values();
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
