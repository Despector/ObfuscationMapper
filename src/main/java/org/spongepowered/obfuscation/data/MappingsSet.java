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
package org.spongepowered.obfuscation.data;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A set of mappings of obfuscated names to plain names.
 */
public class MappingsSet {

    private final BiMap<String, String> packages = HashBiMap.create();
    private final BiMap<String, String> classes = HashBiMap.create();
    private final BiMap<String, String> fields = HashBiMap.create();
    private final Multimap<String, MethodMapping> methods = HashMultimap.create();
    private final Multimap<String, MethodMapping> inverse_methods = HashMultimap.create();

    private boolean modified = false;

    public MappingsSet() {
    }

    /**
     * Gets if this mapping set has been modified since the modification flag
     * was last cleared.
     */
    public boolean isModified() {
        return this.modified;
    }

    /**
     * Clears the modification flag to mark all previous modifications as
     * handled.
     */
    public void unmarkModified() {
        this.modified = false;
    }

    /**
     * Maps an obfuscated package name to plain, returns null if no mapping is
     * found.
     */
    public String mapPackage(String pkg) {
        return this.packages.get(pkg);
    }

    /**
     * Maps an obfuscated package name to plain returning the obfuscated name if
     * no mapping was found.
     */
    public String mapPackageSafe(String pkg) {
        String mapped = this.packages.get(pkg);
        if (mapped == null) {
            return pkg;
        }
        return mapped;
    }

    /**
     * Maps a plain package name to its obfuscated counterpart, returns null if
     * no mapping is found.
     */
    public String inversePackage(String mapped) {
        return this.packages.inverse().get(mapped);
    }

    /**
     * Adds a package mapping. If a mapping with the given obfuscated name
     * already exists then the mapping will fail.
     */
    public void addPackageMapping(String obfuscated_package, String mapped_package) {
        String existing = this.packages.get(obfuscated_package);
        if (existing != null) {
            System.out.println("Updating package mapping for " + obfuscated_package + " from " + existing + " to " + mapped_package);
            return;
        }
        this.packages.put(obfuscated_package, mapped_package);
        this.modified = true;
    }

    /**
     * Gets a set of the obfuscated names of all mapped packages.
     */
    public Set<String> getMappedPackages() {
        return this.packages.keySet();
    }

    /**
     * Gets a collection of the plain names of all mapped packages.
     */
    public Collection<String> getMappedPackageNames() {
        return this.packages.values();
    }

    /**
     * Gets how many packages are mapped.
     */
    public int packagesCount() {
        return this.packages.size();
    }

    public String mapType(String obfuscated_type) {
        return this.classes.get(obfuscated_type);
    }

    /**
     * Maps the given obfuscated type name to its mapped equivalent. Returns the
     * original obfuscated name if not such mapping is found.
     */
    public String mapTypeSafe(String obfuscated_type) {
        String mapped = this.classes.get(obfuscated_type);
        if (mapped == null) {
            return obfuscated_type;
        }
        return mapped;
    }

    /**
     * Gets the obfuscated name for the given plain name, or null if not found.
     */
    public String inverseType(String mapped_type) {
        return this.classes.inverse().get(mapped_type);
    }

    /**
     * Adds the given type mapping. If a mapping for the given obfuscated name
     * already exists then this mapping is ignored.
     */
    public void addTypeMapping(String obfuscated_type, String mapped_type) {
        String existing = this.classes.get(obfuscated_type);
        if (existing != null && !existing.equals(mapped_type)) {
            System.out.println("Existing class mapping for " + obfuscated_type + " from " + existing + " to " + mapped_type);
        }
        this.classes.put(obfuscated_type, mapped_type);
        this.modified = true;
    }

    public int typeCount() {
        return this.classes.size();
    }

    public Set<String> getMappedTypes() {
        return this.classes.keySet();
    }

    public Collection<String> getMappedTypeNames() {
        return this.classes.values();
    }

    /**
     * Gets the plain name for the given obfuscated field name for the given
     * owner. The owner name shoud be the obfuscated name of the owner. Returns
     * null if no mapping is found.
     */
    public String mapField(String owner, String fld) {
        String key = owner + "/" + fld;
        return mapField(key);
    }

    /**
     * Gets the mapped name for a field key. A field key is formed by the
     * obfuscated owner name and the obfuscated field name combined together
     * separated by a '/'.
     */
    public String mapField(String key) {
        String mapped = this.fields.get(key);
        if (mapped == null) {
            return null;
        }
        String mapped_fld = mapped.substring(mapped.lastIndexOf('/') + 1, mapped.length());
        return mapped_fld;
    }

    /**
     * Gets the plain name for the given obfuscated field name for the given
     * owner. The owner name should be the obfuscated name of the owner. If no
     * mapping is found then the obfuscated name is returned.
     */
    public String mapFieldSafe(String owner, String fld) {
        String mapped = mapField(owner, fld);
        return mapped == null ? fld : mapped;
    }

    /**
     * Returns the obfuscated field name given a mapped owner name and mapped
     * field name. Returns null if no such mapping is found.
     */
    public String inverseField(String owner, String mapped) {
        String key = owner + "/" + mapped;
        String inverse = this.fields.inverse().get(key);
        if (inverse == null) {
            return null;
        }
        String obf_fld = inverse.substring(inverse.lastIndexOf('/') + 1, inverse.length());
        return obf_fld;
    }

    /**
     * Adds a new field mapping to this mapping set. The owner name should be
     * given in obfuscated form.
     * 
     * <p>A mapping for the owner must exist before registering a field mapping
     * for it. If the owner is not obfuscated then a trivial mapping should be
     * inserted as a placeholder.</p>
     */
    public void addFieldMapping(String owner, String old, String mapped) {
        String key = owner + "/" + old;
        String existing = this.fields.get(key);
        if (existing != null) {
            System.out.println("Updating field mapping for " + key + " from " + existing + " to " + mapped);
            return;
        }
        String mapped_owner = mapType(owner);
        if (mapped_owner == null) {
            throw new IllegalStateException("Tried to map field before type");
        }
        this.fields.put(key, mapped_owner + "/" + mapped);
        this.modified = true;
    }

    public int fieldCount() {
        return this.fields.size();
    }

    /**
     * Gets the <strong>keys</strong> of all mapped fields. The key is made up
     * of `owner_name/field_name`.
     */
    public Set<String> getMappedFields() {
        return this.fields.keySet();
    }

    public Collection<String> getMappedFieldNames() {
        return this.fields.values();
    }

    /**
     * Gets the method mapping for the given obfuscated method name and
     * signature. If no such method is found then null is returned.
     */
    public MethodMapping getMethodMapping(String owner, String obfuscated_method, String obfuscated_signature) {
        String key = owner + "/" + obfuscated_method;
        for (MethodMapping pos : this.methods.get(key)) {
            if (pos.obfuscated_signature.equals(obfuscated_signature)) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Maps the given obfuscated method name and signature to the mapped method
     * name, if no such method exists then null is returned.
     */
    public String mapMethod(String owner, String method, String sig) {
        String key = owner + "/" + method;
        for (MethodMapping pos : this.methods.get(key)) {
            if (pos.isMapped() && pos.obfuscated_signature.equals(sig)) {
                return pos.map_method;
            }
        }
        return null;
    }

    /**
     * Maps the given obfuscated method name and signature to the mapped method
     * name, if no such method exists then the obfuscated method name is
     * returned.
     */
    public String mapMethodSafe(String owner, String method, String sig) {
        String key = owner + "/" + method;
        for (MethodMapping pos : this.methods.get(key)) {
            if (pos.isMapped() && pos.obfuscated_signature.equals(sig)) {
                return pos.map_method;
            }
        }
        return method;
    }

    public String inverseMethod(String owner, String method, String obf_desc) {
        String key = owner + "/" + method;
        for (MethodMapping pos : this.inverse_methods.get(key)) {
            if (pos.obfuscated_signature.equals(obf_desc)) {
                return pos.obf_method;
            }
        }
        return null;
    }

    /**
     * Adds a new method mapping.
     */
    public void addMethodMapping(String obfuscated_owner, String obfuscated_method, String obfuscated_signature, String mapped_method) {
        String key = obfuscated_owner + "/" + obfuscated_method;
        MethodMapping existing = null;
        for (MethodMapping pos : this.methods.get(key)) {
            if (pos.obfuscated_signature.equals(obfuscated_signature)) {
                existing = pos;
                break;
            }
        }
        if (existing == null) {
            MethodMapping mapping = new MethodMapping(this, obfuscated_owner, obfuscated_method, obfuscated_signature, mapped_method);
            this.methods.put(key, mapping);
            String mapped_owner = mapTypeSafe(obfuscated_owner);
            this.inverse_methods.put(mapped_owner + "/" + mapped_method, mapping);
            this.modified = true;
        }
    }

    public int methodCount() {
        return this.methods.values().size();
    }

    public Collection<MethodMapping> getMethods(String obfuscated_owner, String obfuscated_method) {
        return this.methods.get(obfuscated_owner + "/" + obfuscated_method);
    }

    public Collection<MethodMapping> getMethods(String key) {
        return this.methods.get(key);
    }

    /**
     * Gets the <strong>keys</strong> of all mapped fields. The key is made up
     * of `owner_name/field_name`.
     */
    public Set<String> getMappedMethods() {
        return this.methods.keySet();
    }

    /**
     * Merges this mapping set with the given mapping set. The new mappings are
     * given preference over the existing mappings and will replace them if they
     * conflict.
     */
    public void merge(MappingsSet other) {
        final int initial_size = packagesCount() + typeCount() + methodCount() + fieldCount();
        for (Map.Entry<String, String> e : other.packages.entrySet()) {
            this.packages.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : other.classes.entrySet()) {
            this.classes.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : other.fields.entrySet()) {
            this.fields.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Collection<MethodMapping>> e : other.methods.asMap().entrySet()) {
            for (MethodMapping mth : e.getValue()) {
                this.methods.put(e.getKey(), new MethodMapping(mth));
            }
        }
        int final_size = packagesCount() + typeCount() + methodCount() + fieldCount();
        if (initial_size != final_size) {
            this.modified = true;
        }
    }

    /**
     * Represents a mapping of a method as a helper for mapping signature
     * information.
     */
    public static class MethodMapping {

        private final MappingsSet set;
        final String obf_cls;
        final String obf_method;
        final String obfuscated_signature;
        String map_method;
        String map_sig;

        /**
         * Creates a new method mapping with a mapped name specified.
         */
        public MethodMapping(MappingsSet set, String c, String m, String s, String mm) {
            this.set = set;
            this.obf_cls = c;
            this.obf_method = m;
            this.obfuscated_signature = s;
            this.map_method = mm;
            updateSig();
        }

        /**
         * Creates a new method mapping without a mapped name specified.
         */
        public MethodMapping(MappingsSet set, String c, String m, String s) {
            this.set = set;
            this.obf_cls = c;
            this.obf_method = m;
            this.obfuscated_signature = s;
        }

        /**
         * Creates a method mapping as a clone of the given mapping.
         */
        public MethodMapping(MethodMapping clone) {
            this.set = clone.set;
            this.obf_cls = clone.obf_cls;
            this.obf_method = clone.obf_method;
            this.obfuscated_signature = clone.obfuscated_signature;
            this.map_method = clone.map_method;
            this.map_sig = clone.map_sig;
        }

        public String getObfOwner() {
            return this.obf_cls;
        }

        public String getObf() {
            return this.obf_method;
        }

        public String getObfSignature() {
            return this.obfuscated_signature;
        }

        public String getMapped() {
            return this.map_method;
        }

        public String getMappedSignature() {
            updateSig();
            return this.map_sig;
        }

        public boolean isMapped() {
            return this.map_method != null;
        }

        public void update(String mth) {
            this.map_method = mth;
            updateSig();
        }

        public void updateSig() {
            this.map_sig = mapSig(this.obfuscated_signature, this.set);
        }

        public static String mapDesc(String desc, MappingsSet map) {
            if (desc.startsWith("[")) {
                return "[" + mapDesc(desc.substring(1), map);
            }
            if (desc.startsWith("L")) {
                String cls = desc.substring(1, desc.length() - 1);
                return "L" + map.mapTypeSafe(cls) + ";";
            }
            return desc;
        }

        public static String mapSig(String desc, MappingsSet map) {
            String out = "(";
            String accu = "";
            for (int i = 1; i < desc.length(); i++) {
                char next = desc.charAt(i);
                if (next == ')') {
                    break;
                }
                if (next == '[') {
                    accu += next;
                } else if (next == 'L') {
                    while (next != ';') {
                        accu += next;
                        next = desc.charAt(++i);
                    }
                    accu += ';';
                    out += mapDesc(accu, map);
                    accu = "";
                } else {
                    accu += next;
                    out += mapDesc(accu, map);
                    accu = "";
                }
            }
            String ret = desc.substring(desc.indexOf(')') + 1);
            out += ")" + mapDesc(ret, map);
            return out;
        }
    }
}
