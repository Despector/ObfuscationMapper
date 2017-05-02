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

import org.spongepowered.despector.ast.type.MethodEntry;
import org.spongepowered.obfuscation.data.MappingsSet;
import org.spongepowered.obfuscation.merge.MergeEngine;
import org.spongepowered.obfuscation.merge.MergeOperation;
import org.spongepowered.obfuscation.merge.data.MatchEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MatchDiscreteMethods implements MergeOperation {

    @Override
    public void operate(MergeEngine set) {

        for (MatchEntry type_match : set.getAllMatches()) {
            Map<String, MethodEntry> new_discrete = new HashMap<>();
            Set<String> invalid = new HashSet<>();

            for (MethodEntry mth : type_match.getNewType().getMethods()) {
                if (set.isMethodMatched(mth)) {
                    continue;
                }
                String key = makeKey(mth.getDescription(), set.getNewMappings());
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
            Map<String, MethodEntry> old_discrete = new HashMap<>();

            for (MethodEntry mth : type_match.getOldType().getMethods()) {
                if (set.isMethodMatched(mth)) {
                    continue;
                }
                String key = makeOldKey(mth.getDescription(), set.getOldMappings(), set.getNewMappings());
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

            for (Map.Entry<String, MethodEntry> e : new_discrete.entrySet()) {
                MethodEntry old = old_discrete.get(e.getKey());
                if (old == null) {
                    continue;
                }
                set.vote(old, e.getValue());
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

    private String makeKey(String desc, MappingsSet map) {
        String out = "(";
        for (int i = 1; i < desc.length(); i++) {
            char next = desc.charAt(i);
            if (next == ')') {
                break;
            }
            String accu = "";
            if (next == '[') {
                accu += next;
            } else if (next == 'L') {
                while (next != ';') {
                    accu += next;
                    next = desc.charAt(++i);
                }
                accu += ';';
                String m = mapDesc(accu, map);
                if (m == null) {
                    out += "*";
                } else {
                    out += m;
                }
                accu = "";
            } else {
                accu += next;
                String m = mapDesc(accu, map);
                if (m == null) {
                    out += "*";
                } else {
                    out += m;
                }
                accu = "";
            }
        }
        String ret = desc.substring(desc.indexOf(')') + 1);
        out += ")";
        String m = mapDesc(ret, map);
        if (m == null) {
            out += "*";
        } else {
            out += m;
        }
        return out;
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

    private String makeOldKey(String desc, MappingsSet old_map, MappingsSet new_map) {
        String out = "(";
        for (int i = 1; i < desc.length(); i++) {
            char next = desc.charAt(i);
            if (next == ')') {
                break;
            }
            String accu = "";
            if (next == '[') {
                accu += next;
            } else if (next == 'L') {
                while (next != ';') {
                    accu += next;
                    next = desc.charAt(++i);
                }
                accu += ';';
                String m = mapOldDesc(accu, old_map, new_map);
                if (m == null) {
                    out += "*";
                } else {
                    out += m;
                }
                accu = "";
            } else {
                accu += next;
                String m = mapOldDesc(accu, old_map, new_map);
                if (m == null) {
                    out += "*";
                } else {
                    out += m;
                }
                accu = "";
            }
        }
        String ret = desc.substring(desc.indexOf(')') + 1);
        out += ")";
        String m = mapOldDesc(ret, old_map, new_map);
        if (m == null) {
            out += "*";
        } else {
            out += m;
        }
        return out;
    }

}
