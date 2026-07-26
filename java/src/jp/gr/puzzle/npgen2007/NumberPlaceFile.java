/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * Java 17 reference rewrite derived from NPGenerator V2.0.2.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package jp.gr.puzzle.npgen2007;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * The data fields and XML vocabulary used by the original NumberPlaceFile.
 */
public class NumberPlaceFile {
    private int numSize = -1;
    private boolean[] hint;
    private int[] hidden;
    private int[] answer;
    private int[] problem;
    private int[] blockArray;
    private final List<int[]> groupArrays = new ArrayList<>();
    private int[] seed;
    private String comment;
    private boolean hasHint;
    private boolean vertical = true;
    private boolean horizontal = true;
    private boolean diagonal;
    private boolean defaultBlock = true;
    private int difficult = -1;

    public NumberPlaceFile() {
    }

    public NumberPlaceFile(File file) throws IOException {
        load(file);
    }

    public boolean isDefaultBlock() {
        return defaultBlock;
    }

    public void setDefaultBlock(boolean value) {
        defaultBlock = value;
    }

    public void setNumSize(int value) {
        numSize = value;
    }

    public int getNumSize() {
        return numSize;
    }

    public void setHint(boolean[] value) {
        hint = value;
        hasHint = value != null;
    }

    public boolean[] getHint() {
        return hint;
    }

    public boolean hasHint() {
        return hasHint;
    }

    public void setHidden(int[] value) {
        hidden = value;
    }

    public int[] getHidden() {
        return hidden;
    }

    public void setAnswer(int[] value) {
        answer = value;
    }

    public int[] getAnswer() {
        return answer;
    }

    public void setProblem(int[] value) {
        problem = value;
    }

    public int[] getProblem() {
        return problem;
    }

    public void setBlockArray(int[] value) {
        blockArray = value;
    }

    public int[] getBlockArray() {
        return blockArray;
    }

    public List<int[]> getGroupArrays() {
        List<int[]> result = new ArrayList<>(groupArrays.size());
        for (int[] group : groupArrays) {
            result.add(group.clone());
        }
        return Collections.unmodifiableList(result);
    }

    public int[] getSeed() {
        return seed;
    }

    public void setSeed(int[] value) {
        seed = value;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String value) {
        comment = value;
    }

    public boolean isVertical() {
        return vertical;
    }

    public void setVertical(boolean value) {
        vertical = value;
    }

    public boolean isHorizontal() {
        return horizontal;
    }

    public void setHorizontal(boolean value) {
        horizontal = value;
    }

    public boolean isDiagonal() {
        return diagonal;
    }

    public void setIsDiagonal(boolean value) {
        diagonal = value;
    }

    public void setDifficult(int value) {
        difficult = value;
    }

    public int getDifficult() {
        return difficult;
    }

    public void load(File file) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(file);
            Element root = document.getDocumentElement();
            if (!root.getTagName().equals("problem")) {
                throw new IOException(file + ": root element must be <problem>");
            }
            numSize = parseSize(root.getAttribute("size"), file);
            int cells = numSize * numSize;
            problem = parseIntArray(text(root, "question"), cells);
            String hintText = text(root, "hint");
            hasHint = hintText != null;
            hint = parseBooleanArray(hintText, cells);
            hidden = parseIntArray(text(root, "hidden"), cells);
            answer = parseIntArray(text(root, "answer"), cells);
            String seedText = text(root, "seed");
            seed = seedText == null ? null : parseIntArray(seedText, cells);
            comment = text(root, "comment");

            NodeList constraints = root.getElementsByTagName("constraint");
            if (constraints.getLength() == 0) {
                throw new IOException(file + ": missing <constraint>");
            }
            Element constraint = (Element) constraints.item(0);
            vertical = !constraint.hasAttribute("vertical")
                    || "on".equals(constraint.getAttribute("vertical"));
            horizontal = !constraint.hasAttribute("horizonal")
                    || "on".equals(constraint.getAttribute("horizonal"));
            diagonal = "on".equals(constraint.getAttribute("diagonal"));
            defaultBlock = "on".equals(constraint.getAttribute("default-block"));
            groupArrays.clear();
            NodeList groups = constraint.getElementsByTagName("group");
            for (int index = 0; index < groups.getLength(); index++) {
                groupArrays.add(parseIntArray(groups.item(index).getTextContent(), cells));
            }
            blockArray = defaultBlock || groupArrays.isEmpty()
                    ? null : groupArrays.get(0).clone();
            NodeList questions = root.getElementsByTagName("question");
            if (questions.getLength() > 0) {
                String value = ((Element) questions.item(0)).getAttribute("difficult");
                if (!value.isEmpty()) {
                    try {
                        difficult = Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                        difficult = -1;
                    }
                }
            }
        } catch (ParserConfigurationException | SAXException error) {
            throw new IOException("cannot parse XML " + file + ": " + error.getMessage(), error);
        }
    }

    public void Load(File file) throws IOException {
        load(file);
    }

    public String toXmlString() throws IOException {
        validateForSave();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
        xml.append("<problem size=\"").append(numSize)
                .append("\" name=\"Number Place\" author=\"Number Place Generator\">");
        if (problem != null) {
            xml.append("<question difficult=\"").append(difficult).append("\">")
                    .append(join(problem)).append("</question>");
        }
        xml.append("<constraint default-block=\"")
                .append(defaultBlock ? "on" : "off")
                .append("\" diagonal=\"").append(diagonal ? "on" : "off").append("\"");
        if (!vertical) {
            xml.append(" vertical=\"off\"");
        }
        if (!horizontal) {
            xml.append(" horizonal=\"off\"");
        }
        xml.append(">");
        if (!defaultBlock) {
            xml.append("<group block=\"on\">").append(join(blockArray)).append("</group>");
        }
        xml.append("</constraint>");
        if (answer != null) {
            xml.append("<answer>").append(join(answer)).append("</answer>");
        }
        if (hint != null) {
            xml.append("<hint>").append(join(hint)).append("</hint>");
        }
        if (hidden != null) {
            xml.append("<hidden>").append(join(hidden)).append("</hidden>");
        }
        if (comment != null) {
            xml.append("<comment>").append(escapeText(comment)).append("</comment>");
        }
        xml.append("</problem>\n");
        return xml.toString();
    }

    public void save(File file) throws IOException {
        Files.writeString(file.toPath(), toXmlString(), StandardCharsets.UTF_8);
    }

    public void Save(File file) {
        try {
            save(file);
        } catch (IOException error) {
            throw new IllegalStateException("cannot save " + file, error);
        }
    }

    private void validateForSave() throws IOException {
        if (numSize < 2 || numSize > 25) {
            throw new IOException("size must be between 2 and 25");
        }
        int cells = numSize * numSize;
        requireLength(problem, cells, "problem");
        requireLength(answer, cells, "answer");
        requireLength(hidden, cells, "hidden");
        requireLength(seed, cells, "seed");
        if (hint != null && hint.length != cells) {
            throw new IOException("hint must contain " + cells + " cells");
        }
        if (!defaultBlock) {
            if (blockArray == null || blockArray.length != cells) {
                throw new IOException("block array must contain " + cells + " cells");
            }
        }
    }

    private static void requireLength(int[] value, int cells, String name) throws IOException {
        if (value != null && value.length != cells) {
            throw new IOException(name + " must contain " + cells + " cells");
        }
    }

    private static int parseSize(String value, File file) throws IOException {
        try {
            int size = Integer.parseInt(value);
            if (size < 2 || size > 25) {
                throw new IOException(file + ": size must be between 2 and 25");
            }
            return size;
        } catch (NumberFormatException error) {
            throw new IOException(file + ": invalid problem size");
        }
    }

    private static String text(Element root, String name) {
        NodeList elements = root.getElementsByTagName(name);
        return elements.getLength() == 0 ? null : elements.item(0).getTextContent();
    }

    private static int[] parseIntArray(String value, int length) {
        int[] result = new int[length];
        if (value == null) {
            return result;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return result;
        }
        String[] tokens = trimmed.split("\\s+");
        for (int index = 0; index < Math.min(tokens.length, length); index++) {
            try {
                result[index] = Integer.parseInt(tokens[index]);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("invalid integer in XML: " + tokens[index]);
            }
        }
        return result;
    }

    private static boolean[] parseBooleanArray(String value, int length) {
        int[] integers = parseIntArray(value, length);
        boolean[] result = new boolean[length];
        for (int index = 0; index < length; index++) {
            result[index] = integers[index] != 0;
        }
        return result;
    }

    private static String join(int[] values) {
        return Utility.toStringFromArray(values);
    }

    private static String join(boolean[] values) {
        return Utility.toStringFromArray(values);
    }

    private static String escapeText(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
