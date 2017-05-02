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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class MergeEngine {

    public static MergeOperation jumpTo(int index, Predicate<MergeEngine> condition) {
        return new JumpOperation(index, condition);
    }

    private List<MergeOperation> operations = new ArrayList<>();

    private final Map<TypeEntry, MatchEntry> matches = new HashMap<>();
    private final Set<TypeEntry> matched_types = new HashSet<>();
    private final Map<MethodEntry, MethodMatchEntry> method_matches = new HashMap<>();
    private final Set<MethodEntry> matched_methods = new HashSet<>();
    private final Map<FieldEntry, FieldMatchEntry> field_matches = new HashMap<>();
    private final Set<FieldEntry> matched_fields = new HashSet<>();

    private final Map<TypeEntry, MatchEntry> pending_matches = new HashMap<>();
    private final Map<MethodEntry, MethodMatchEntry> pending_method_matches = new HashMap<>();
    private final Map<FieldEntry, FieldMatchEntry> pending_field_matches = new HashMap<>();

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

    public void incrementChangeCount() {
        this.changes_last_cycle++;
    }

    public void resetChanges() {
        this.changes_last_cycle = 0;
    }

    public MatchEntry getMatch(TypeEntry t) {
        return this.matches.get(t);
    }

    public MatchEntry getPendingMatch(TypeEntry t) {
        MatchEntry m = getMatch(t);
        if (m != null) {
            return m;
        }
        m = this.pending_matches.get(t);
        if (m == null) {
            m = new MatchEntry(t);
            this.pending_matches.put(t, m);
        }
        return m;
    }

    public boolean vote(TypeEntry old, TypeEntry n) {
        MatchEntry m = getPendingMatch(old);
        if (this.matched_types.contains(n)) {
            return m.getNewType() == n;
        }
        return m.vote(n);
    }

    public void setAsMatched(MatchEntry entry) {
        if (entry.getNewType() == null) {
            throw new IllegalStateException();
        }
        this.pending_matches.remove(entry.getOldType());
        this.matches.put(entry.getOldType(), entry);
        this.matched_types.add(entry.getNewType());
        for (MatchEntry match : this.pending_matches.values()) {
            match.removeVote(entry.getNewType());
        }
    }

    public Collection<MatchEntry> getAllMatches() {
        return this.matches.values();
    }

    public Collection<MatchEntry> getPendingMatches() {
        return this.pending_matches.values();
    }

    public MethodMatchEntry getMethodMatch(MethodEntry t) {
        return this.method_matches.get(t);
    }

    public MethodMatchEntry getPendingMethodMatch(MethodEntry t) {
        MethodMatchEntry m = getMethodMatch(t);
        if (m != null) {
            return m;
        }
        m = this.pending_method_matches.get(t);
        if (m == null) {
            m = new MethodMatchEntry(t);
            this.pending_method_matches.put(t, m);
        }
        return m;
    }

    public boolean vote(MethodEntry old, MethodEntry n) {
        MethodMatchEntry m = getPendingMethodMatch(old);
        if (this.matched_methods.contains(n)) {
            return m.getNewMethod() == n;
        }
        return m.vote(n);
    }

    public boolean isMethodMatched(MethodEntry n) {
        return this.matched_methods.contains(n) || this.method_matches.containsKey(n);
    }

    public void setAsMatched(MethodMatchEntry entry) {
        if (entry.getNewMethod() == null) {
            throw new IllegalStateException();
        }
        this.pending_method_matches.remove(entry.getOldMethod());
        this.method_matches.put(entry.getOldMethod(), entry);
        this.matched_methods.add(entry.getNewMethod());
        for (MethodMatchEntry match : this.pending_method_matches.values()) {
            match.removeVote(entry.getNewMethod());
        }
    }

    public Collection<MethodMatchEntry> getAllMethodMatches() {
        return this.method_matches.values();
    }

    public Collection<MethodMatchEntry> getPendingMethodMatches() {
        return this.pending_method_matches.values();
    }

    public FieldMatchEntry getFieldMatch(FieldEntry t) {
        return this.field_matches.get(t);
    }

    public FieldMatchEntry getPendingFieldMatch(FieldEntry t) {
        FieldMatchEntry m = getFieldMatch(t);
        if (m != null) {
            return m;
        }
        m = this.pending_field_matches.get(t);
        if (m == null) {
            m = new FieldMatchEntry(t);
            this.pending_field_matches.put(t, m);
        }
        return m;
    }

    public boolean vote(FieldEntry old, FieldEntry n) {
        FieldMatchEntry m = getPendingFieldMatch(old);
        if (this.matched_fields.contains(n)) {
            return m.getNewField() == n;
        }
        return m.vote(n);
    }

    public void setAsMatched(FieldMatchEntry entry) {
        if (entry.getNewField() == null) {
            throw new IllegalStateException();
        }
        this.pending_field_matches.remove(entry.getOldField());
        this.field_matches.put(entry.getOldField(), entry);
        this.matched_fields.add(entry.getNewField());
        for (FieldMatchEntry match : this.pending_field_matches.values()) {
            match.removeVote(entry.getNewField());
        }
    }

    public Collection<FieldMatchEntry> getAllFieldMatches() {
        return this.field_matches.values();
    }

    public Collection<FieldMatchEntry> getPendingFieldMatches() {
        return this.pending_field_matches.values();
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
            String mapped = this.old_mappings.mapType(entry.getOldType().getName());
            if (mapped != null) {
                this.new_mappings.addTypeMapping(entry.getNewType().getName(), mapped);
            }
        }

        for (FieldMatchEntry entry : this.field_matches.values()) {
            String owner = entry.getOldField().getOwnerName();
            String mapped = this.old_mappings.mapField(owner, entry.getOldField().getName());
            if (mapped != null) {
                String new_owner = entry.getNewField().getOwnerName();
                this.new_mappings.addFieldMapping(new_owner, entry.getNewField().getName(), mapped);
            }
        }

        for (MethodMatchEntry entry : this.method_matches.values()) {
            String owner = entry.getOldMethod().getOwnerName();
            String mapped = this.old_mappings.mapMethod(owner, entry.getOldMethod().getName(), entry.getOldMethod().getDescription());
            if (mapped != null) {
                String new_owner = entry.getNewMethod().getOwnerName();
                this.new_mappings.addMethodMapping(new_owner, entry.getNewMethod().getName(), entry.getNewMethod().getDescription(), mapped);
            }
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
