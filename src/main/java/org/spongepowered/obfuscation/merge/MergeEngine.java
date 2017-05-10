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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.spongepowered.despector.Language;
import org.spongepowered.despector.ast.AstVisitor;
import org.spongepowered.despector.ast.SourceSet;
import org.spongepowered.despector.ast.generic.ClassTypeSignature;
import org.spongepowered.despector.ast.generic.MethodSignature;
import org.spongepowered.despector.ast.generic.TypeSignature;
import org.spongepowered.despector.ast.type.ClassEntry;
import org.spongepowered.despector.ast.type.FieldEntry;
import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.despector.ast.type.TypeEntry;
import org.spongepowered.despector.util.TypeHelper;
import org.spongepowered.despector.util.serialization.MessagePacker;
import org.spongepowered.obfuscation.data.MappingsSet;
import org.spongepowered.obfuscation.merge.data.FieldMatchEntry;
import org.spongepowered.obfuscation.merge.data.MatchEntry;
import org.spongepowered.obfuscation.merge.data.MethodGroup;
import org.spongepowered.obfuscation.merge.data.MethodMatchEntry;
import org.spongepowered.obfuscation.util.MethodGroupBuilder;

import java.io.IOException;
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
    private final Map<TypeEntry, MatchEntry> matches_inverse = new HashMap<>();
    private final Map<MethodEntry, MethodMatchEntry> method_matches = new HashMap<>();
    private final Map<MethodEntry, MethodMatchEntry> method_matches_inverse = new HashMap<>();
    private final Map<FieldEntry, FieldMatchEntry> field_matches = new HashMap<>();
    private final Map<FieldEntry, FieldMatchEntry> field_matches_inverse = new HashMap<>();

    private final Map<TypeEntry, MatchEntry> pending_matches = new HashMap<>();
    private final Map<MethodEntry, MethodMatchEntry> pending_method_matches = new HashMap<>();
    private final Map<FieldEntry, FieldMatchEntry> pending_field_matches = new HashMap<>();

    private final Map<MethodEntry, MethodGroup> old_method_groups = new HashMap<>();
    private final Map<MethodEntry, MethodGroup> new_method_groups = new HashMap<>();

    private final Multimap<TypeEntry, TypeEntry> old_subtypes = HashMultimap.create();
    private final Multimap<TypeEntry, TypeEntry> new_subtypes = HashMultimap.create();

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

    public MatchEntry getMatchInverse(TypeEntry t) {
        return this.matches_inverse.get(t);
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
        if (this.matches_inverse.containsKey(n)) {
            return m.getNewType() == n;
        }
        return m.vote(n);
    }

    public boolean isTypeMatched(TypeEntry n) {
        return this.matches_inverse.containsKey(n) || this.matches.containsKey(n);
    }

    public void setAsMatched(MatchEntry entry) {
        if (entry.getNewType() == null) {
            throw new IllegalStateException();
        }
        this.pending_matches.remove(entry.getOldType());
        this.matches.put(entry.getOldType(), entry);
        this.matches_inverse.put(entry.getNewType(), entry);
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

    public MethodGroup getOldMethodGroup(MethodEntry entry) {
        MethodGroup group = this.old_method_groups.get(entry);
        if (group == null) {
            group = new MethodGroup(entry);
            this.old_method_groups.put(entry, group);
        }
        return group;
    }

    public MethodGroup getNewMethodGroup(MethodEntry entry) {
        MethodGroup group = this.new_method_groups.get(entry);
        if (group == null) {
            group = new MethodGroup(entry);
            this.new_method_groups.put(entry, group);
        }
        return group;
    }

    public MethodMatchEntry getMethodMatch(MethodEntry t) {
        return this.method_matches.get(t);
    }

    public MethodMatchEntry getMethodMatchInverse(MethodEntry t) {
        return this.method_matches_inverse.get(t);
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
        if (this.method_matches_inverse.containsKey(n)) {
            return m.getNewMethod() == n;
        }
        return m.vote(n);
    }

    public boolean isMethodMatched(MethodEntry n) {
        return this.method_matches_inverse.containsKey(n) || this.method_matches.containsKey(n);
    }

    public void setAsMatched(MethodMatchEntry entry) {
        if (entry.getNewMethod() == null) {
            throw new IllegalStateException();
        }
        this.pending_method_matches.remove(entry.getOldMethod());
        this.method_matches.put(entry.getOldMethod(), entry);
        this.method_matches_inverse.put(entry.getNewMethod(), entry);
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

    public FieldMatchEntry getFieldMatchInverse(FieldEntry t) {
        return this.field_matches_inverse.get(t);
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
        if (this.field_matches_inverse.containsKey(n)) {
            return m.getNewField() == n;
        }
        return m.vote(n);
    }

    public boolean isFieldMatched(FieldEntry n) {
        return this.field_matches_inverse.containsKey(n) || this.field_matches.containsKey(n);
    }

    public void setAsMatched(FieldMatchEntry entry) {
        if (entry.getNewField() == null) {
            throw new IllegalStateException();
        }
        this.pending_field_matches.remove(entry.getOldField());
        this.field_matches.put(entry.getOldField(), entry);
        this.field_matches_inverse.put(entry.getNewField(), entry);
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

        for (String type : this.new_mappings.getMappedTypes()) {
            TypeEntry new_type = this.new_src.get(type);
            if (new_type == null) {
                continue;
            }
            String mapped = this.new_mappings.mapType(type);
            String old = this.old_mappings.inverseType(mapped);
            if (old != null) {
                TypeEntry old_type = this.old_src.get(old);
                if (old_type != null) {
                    MatchEntry match = getPendingMatch(old_type);
                    match.setNewType(new_type);
                    setAsMatched(match);
                    continue;
                }
            }
            TypeEntry dummy = createDummyType(this.old_src, old);
            MatchEntry match = getPendingMatch(dummy);
            match.setNewType(new_type);
            setAsMatched(match);
        }

        generateSubtypes();
        generateMethodGroups();

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

        for (TypeEntry type : this.new_src.getAllClasses()) {
            if (!type.isAnonType()) {
                continue;
            }
            if (this.new_mappings.mapType(type.getName()) != null) {
                continue;
            }
            String parent = type.getName().substring(0, type.getName().lastIndexOf('$'));
            String parent_mapped = this.new_mappings.mapType(parent);
            if (parent_mapped == null) {
                continue;
            }
            int index = Integer.parseInt(type.getName().substring(type.getName().lastIndexOf('$') + 1, type.getName().length()));
            String mapped_name = parent_mapped + "$" + index;
            while (this.new_mappings.inverseType(mapped_name) != null) {
                index++;
                mapped_name = parent_mapped + "$" + index;
            }
            this.new_mappings.addTypeMapping(type.getName(), mapped_name);
        }

        for (FieldMatchEntry entry : this.field_matches.values()) {
            if (entry.getOldField() instanceof DummyField) {
                FieldEntry fld = entry.getNewField();
                if (this.new_mappings.mapType(fld.getOwnerName()) == null) {
                    continue;
                }
                this.new_mappings.addFieldMapping(fld.getOwnerName(), fld.getName(), entry.getOldField().getName());
            } else {
                String owner = entry.getOldField().getOwnerName();
                String mapped = this.old_mappings.mapField(owner, entry.getOldField().getName());
                if (mapped != null) {
                    FieldEntry fld = entry.getNewField();
                    this.new_mappings.addFieldMapping(fld.getOwnerName(), fld.getName(), mapped);
                }
            }
        }

        for (MethodMatchEntry entry : this.method_matches.values()) {
            if (entry.getOldMethod() instanceof DummyMethod) {
                MethodEntry mth = entry.getNewMethod();
                if (this.new_mappings.mapType(mth.getOwnerName()) == null) {
                    continue;
                }
                this.new_mappings.addMethodMapping(mth.getOwnerName(), mth.getName(), mth.getDescription(), entry.getOldMethod().getName());
            } else {
                String owner = entry.getOldMethod().getOwnerName();
                String mapped = this.old_mappings.mapMethod(owner, entry.getOldMethod().getName(), entry.getOldMethod().getDescription());
                if (mapped != null) {
                    MethodEntry mth = entry.getNewMethod();
                    this.new_mappings.addMethodMapping(mth.getOwnerName(), mth.getName(), mth.getDescription(), mapped);
                }
            }
        }

    }

    private void generateSubtypes() {
        generateSubtypes(this.old_src, this.old_subtypes);
        generateSubtypes(this.new_src, this.new_subtypes);
    }

    private void generateSubtypes(SourceSet src, Multimap<TypeEntry, TypeEntry> subtypes) {
        for (TypeEntry type : src.getAllClasses()) {
            if (type instanceof ClassEntry) {
                ClassEntry cls = (ClassEntry) type;
                TypeEntry spr = src.get(cls.getSuperclassName());
                if (spr != null) {
                    subtypes.put(spr, type);
                }
            }
            for (String intr : type.getInterfaces()) {
                TypeEntry inter = src.get(intr);
                if (inter != null) {
                    subtypes.put(inter, type);
                }
            }
        }
    }

    private void generateMethodGroups() {
        MethodGroupBuilder old_builder = new MethodGroupBuilder(this.old_src, this.old_method_groups, this.old_subtypes);
        old_builder.build();
        MethodGroupBuilder new_builder = new MethodGroupBuilder(this.new_src, this.new_method_groups, this.new_subtypes);
        new_builder.build();
    }

    public static TypeEntry createDummyType(SourceSet set, String name) {
        TypeEntry type = new DummyType(set, Language.JAVA, name);
        return type;
    }

    public static FieldEntry createDummyField(SourceSet set, String name, TypeSignature desc, String owner) {
        FieldEntry fld = new DummyField(set);
        fld.setName(name);
        fld.setType(desc);
        fld.setOwner(owner);
        return fld;
    }

    public static MethodEntry createDummyMethod(SourceSet set, String name, String desc, String owner) {
        MethodEntry mth = new DummyMethod(set);
        MethodSignature sig = new MethodSignature(ClassTypeSignature.of(TypeHelper.getRet(desc)));
        List<String> params = TypeHelper.splitSig(desc);
        for (String param : params) {
            sig.getParameters().add(ClassTypeSignature.of(param));
        }
        mth.setMethodSignature(sig);
        mth.setName(name);
        mth.setDescription(desc);
        mth.setOwner(owner);
        return mth;
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

    public static class DummyType extends TypeEntry {

        public DummyType(SourceSet source, Language lang, String name) {
            super(source, lang, name);
        }

        @Override
        public void accept(AstVisitor visitor) {
        }

        @Override
        public void writeTo(MessagePacker pack) throws IOException {
        }

    }

    public static class DummyMethod extends MethodEntry {

        public DummyMethod(SourceSet source) {
            super(source);
        }

    }

    public static class DummyField extends FieldEntry {

        public DummyField(SourceSet source) {
            super(source);
        }

    }

}
