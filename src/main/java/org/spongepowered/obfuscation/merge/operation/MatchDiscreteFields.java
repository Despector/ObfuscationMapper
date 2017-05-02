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

import org.spongepowered.despector.ast.type.FieldEntry;
import org.spongepowered.obfuscation.data.MappingsSet;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.MergeOperation;
import org.spongepowered.obfuscation.merge.data.MatchEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MatchDiscreteFields implements MergeOperation {

    @Override
    public void operate(MergeEngine set) {

        for (MatchEntry type_match : set.getAllMatches()) {
            {
                Map<String, FieldEntry> new_discrete = new HashMap<>();
                Set<String> invalid = new HashSet<>();

                for (FieldEntry mth : type_match.getNewType().getFields()) {
                    if (set.isFieldMatched(mth)) {
                        continue;
                    }
                    String key = mapDesc(mth.getType().getDescriptor(), set.getNewMappings());
                    if (invalid.contains(key)) {
                        continue;
                    }
                    if (new_discrete.containsKey(key)) {
                        invalid.add(key);
                        new_discrete.remove(key);
                    } else {
                        new_discrete.put(key, mth);
                    }
                }

                invalid.clear();
                Map<String, FieldEntry> old_discrete = new HashMap<>();

                for (FieldEntry mth : type_match.getOldType().getFields()) {
                    if (set.isFieldMatched(mth)) {
                        continue;
                    }
                    String key = mapOldDesc(mth.getType().getDescriptor(), set.getOldMappings(), set.getNewMappings());
                    if (invalid.contains(key)) {
                        continue;
                    }
                    if (old_discrete.containsKey(key)) {
                        invalid.add(key);
                        old_discrete.remove(key);
                    } else {
                        old_discrete.put(key, mth);
                    }
                }

                for (Map.Entry<String, FieldEntry> e : new_discrete.entrySet()) {
                    FieldEntry old = old_discrete.get(e.getKey());
                    if (old == null) {
                        continue;
                    }
                    set.vote(old, e.getValue());
                }
            }
            {
                Map<String, FieldEntry> new_discrete = new HashMap<>();
                Set<String> invalid = new HashSet<>();

                for (FieldEntry mth : type_match.getNewType().getStaticFields()) {
                    if (set.isFieldMatched(mth)) {
                        continue;
                    }
                    String key = mapDesc(mth.getType().getDescriptor(), set.getNewMappings());
                    if (invalid.contains(key)) {
                        continue;
                    }
                    if (new_discrete.containsKey(key)) {
                        invalid.add(key);
                        new_discrete.remove(key);
                    } else {
                        new_discrete.put(key, mth);
                    }
                }

                invalid.clear();
                Map<String, FieldEntry> old_discrete = new HashMap<>();

                for (FieldEntry mth : type_match.getOldType().getStaticFields()) {
                    if (set.isFieldMatched(mth)) {
                        continue;
                    }
                    String key = mapOldDesc(mth.getType().getDescriptor(), set.getOldMappings(), set.getNewMappings());
                    if (invalid.contains(key)) {
                        continue;
                    }
                    if (old_discrete.containsKey(key)) {
                        invalid.add(key);
                        old_discrete.remove(key);
                    } else {
                        old_discrete.put(key, mth);
                    }
                }

                for (Map.Entry<String, FieldEntry> e : new_discrete.entrySet()) {
                    FieldEntry old = old_discrete.get(e.getKey());
                    if (old == null) {
                        continue;
                    }
                    set.vote(old, e.getValue());
                }
            }
        }
    }

    private static String mapDesc(String desc, MappingsSet map) {
        if (desc.startsWith("[")) {
            return "[" + mapDesc(desc.substring(1), map);
        }
        if (desc.startsWith("L")) {
            String cls = desc.substring(1, desc.length() - 1);
            String m = map.mapType(cls);
            if (m == null) {
                return null;
            }
            return "L" + m + ";";
        }
        return desc;
    }

    private static String mapOldDesc(String desc, MappingsSet old_map, MappingsSet new_map) {
        if (desc.startsWith("[")) {
            return "[" + mapOldDesc(desc.substring(1), old_map, new_map);
        }
        if (desc.startsWith("L")) {
            String cls = desc.substring(1, desc.length() - 1);
            String m = old_map.mapType(cls);
            if (new_map.inverseType(m) == null) {
                return null;
            }
            return "L" + m + ";";
        }
        return desc;
    }

}
