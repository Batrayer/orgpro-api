package fr.orgpro.api.orgzly.parser;

import fr.orgpro.api.orgzly.OrgFile;
import fr.orgpro.api.orgzly.OrgHead;
import fr.orgpro.api.orgzly.OrgPatterns;
import fr.orgpro.api.orgzly.OrgStringUtils;
import fr.orgpro.api.project.State;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The actual parsing happens here.
 */
class OrgSaxyParser extends OrgParser {
    private static final int FIRST_HEADING_POSITION_NUMBER = 1;

    private BufferedReader reader;
    private OrgSaxyParserListener listener;

    private Pattern statePattern;

    public OrgSaxyParser(OrgParserSettings settings, Reader reader, OrgSaxyParserListener listener) {
        this.settings = settings;
        this.reader = new BufferedReader(reader);
        this.listener = listener;

        this.statePattern = buildStatePattern(settings.todoKeywords, settings.doneKeywords);
    }

    /**
     * Given a sets of to do and done keywords, builds a regex for matching against heading.
     *
     * @return {@link Pattern} or null if no valid keywords were found.
     */
    private Pattern buildStatePattern(Set<String> todoKeywords, Set<String> doneKeywords) {
        Set<String> allKeywords = new HashSet<>();

        if (todoKeywords != null) {
            for (String keyword: todoKeywords) {
                allKeywords.add(Pattern.quote(keyword));
            }
        }

        if (doneKeywords != null) {
            for (String keyword: doneKeywords) {
                allKeywords.add(Pattern.quote(keyword));
            }
        }

        if (allKeywords.size() > 0) {
            return Pattern.compile("^(" + OrgStringUtils.join(allKeywords, "|") + ")(.*)");
        }

        return null;
    }

    /**
     * Parses file, calling {@link OrgSaxyParserListener} methods while parsing.
     */
    @Override
    public OrgParsedFile parse() throws IOException {
        OrgFile orgFile = new OrgFile();

        String line;
        StringBuilder preface = new StringBuilder();
        OrgNodeInList currentElement = null;
        boolean isAfterHeading = false;
        boolean inProperties = false;
        // boolean inLogbook = false;
        int nextFreePosition = FIRST_HEADING_POSITION_NUMBER;

        /* Count of how many properties are indented. Can be negative. */
        int indentedCount = 0;

        while ((line = reader.readLine()) != null) {

            /* Search for in-buffer setting if headings are not yet encountered. */
            if (currentElement == null) {
                if (orgFile.getSettings().parseLine(line)) {
                    /* Belongs to file. */
                    preface.append(line);
                    preface.append('\n');

                    continue;
                }
            }

            OrgNodeInList head = tryParsingHeading(line, nextFreePosition);

            if (head != null) { /* New heading found! */

                /* Announce previous heading. */
                if (currentElement != null) {
                    trimContent(currentElement.getHead());
                    listener.onHead(currentElement);
                }

                currentElement = head;
                isAfterHeading = true;
                inProperties = false;
                // inLogbook = false;
                nextFreePosition++;

            } else { /* Not a heading. */

                if (currentElement != null) { // Belongs to heading.

                    if (isAfterHeading) { // Just after heading.
                        boolean found = false;

                        /* Time parameters (SCHEDULED etc.). */
                        Matcher m = OrgPatterns.PLANNING_TIMES_P.matcher(line);
                        while (m.find()) {
                            String timeKey = m.group(1);
                            String timeString = m.group(2);

                            switch (timeKey) {
                                case "SCHEDULED:":
                                    currentElement.getHead().setScheduled(/*OrgRange.parse(timeString)*/new Date(timeString));
                                    break;

                                case "CLOSED:":
                                    currentElement.getHead().setClosed(/*OrgRange.parse(timeString)*/new Date(timeString));
                                    break;

                                case "DEADLINE:":
                                    currentElement.getHead().setDeadline(/*OrgRange.parse(timeString)*/new Date(timeString));
                                    break;

                                default:
                                    throw new IllegalStateException("Unknown time key " + timeKey);
                            }

                            found = true;
                        }

                        String lineTrimmed = line.trim();

                        if (!inProperties && ":PROPERTIES:".equals(lineTrimmed)) {
                            inProperties = true;
                            found = true;

                        } else if (inProperties && ":END:".equals(lineTrimmed)) {
                            inProperties = false;
                            found = true;

                        } else if (inProperties) {
                            Matcher propertyMatcher = OrgPatterns.PROPERTY.matcher(lineTrimmed);

                            if (propertyMatcher.find()) {
                                currentElement.head.addProperty(
                                        propertyMatcher.group(1),
                                        propertyMatcher.group(2)
                                );
                            }

                            found = true;
                        }

//                        if (!inLogbook && ":LOGBOOK:".equals(lineTrimmed)) {
//                            currentElement.head.initLogbook();
//                            inLogbook = true;
//                            found = true;
//
//                        } else if (inLogbook && ":END:".equals(lineTrimmed)) {
//                            inLogbook = false;
//                            found = true;
//
//                        } else if (inLogbook) {
//                            currentElement.head.addLog(lineTrimmed);
//                            found = true;
//                        }

                        int spacesBeforeContent = line.indexOf(lineTrimmed);

                        if (found) {
                            /*
                             * Count as indented if there were 1 or more spaces at the beginning.
                             * Correct indentation is level+1 spaces, which could be used instead.
                             */
                            // if (spacesBeforeContent == currentElement.getLevel() + 1) {
                            if (spacesBeforeContent > 0) {
                                indentedCount++;
                            } else {
                                indentedCount--;
                            }
                        }

                        if (found) {
                            // There could still be heading-bound lines.
                            continue;
                        }
                    }

                    currentElement.getHead().appendContent(line);
                    currentElement.getHead().appendContent("\n");

                } else {
                    /* Belongs to file. */
                    preface.append(line);
                    preface.append('\n');
                }

                isAfterHeading = false;
            }
        }

        /* Announce previous heading. */
        if (currentElement != null) {
            trimContent(currentElement.getHead());
            listener.onHead(currentElement);
        }

        /* Finally, set pre-headers text and announce the file. */
        setTrimmedPreface(orgFile, preface.toString());
        orgFile.getSettings().setIndented(indentedCount > 0);
        listener.onFile(orgFile);

        return null;
    }

    private OrgNodeInList tryParsingHeading(String line, int position) {
        OrgNodeInList entry = null;

        Matcher headMatcher = OrgPatterns.HEAD_P.matcher(line);

        if (headMatcher.find()) {
            int level = headMatcher.group(1).length(); // Simply the number of stars.
            String head = headMatcher.group(2);

            entry = parseHeading(position, level, head);
        }

        return entry;
    }

    private OrgNodeInList parseHeading(int position, int level, String str) {
        OrgHead head = new OrgHead();
        OrgNodeInList element = new OrgNodeInList(position, level, head);

        Matcher m;

		/* First assume entire line is a title and no state. */
        String title = str.trim();

        /* Try known states. */
        if (statePattern != null) {
            Matcher stateMatcher = statePattern.matcher(title);
            if (stateMatcher.find()) {
                /* String after keyword must be either empty or start with space. */
                if (stateMatcher.group(2).length() == 0 || stateMatcher.group(2).startsWith(" ")) {
                    /* Update state and title. */
                    if(stateMatcher.group(1).equals(State.TODO.toString())){
                        head.setState(State.TODO);
                    }
                    if(stateMatcher.group(1).equals(State.DONE.toString())){
                        head.setState(State.DONE);
                    }
                    if(stateMatcher.group(1).equals(State.ONGOING.toString())){
                        head.setState(State.ONGOING);
                    }
                    if(stateMatcher.group(1).equals(State.CANCELLED.toString())){
                        head.setState(State.CANCELLED);
                    }
                    title = stateMatcher.group(2).trim();
                }
            }
        }

		/* Parse priority. */
        m = OrgPatterns.HEAD_PRIORITY_P.matcher(title);
        if (m.find()) {
            head.setPriority(m.group(1));
            title = m.group(2).trim();
        }

		/* Parse tags. */
        m = OrgPatterns.HEAD_TAGS_P.matcher(title);
        if (m.find()) {
            title = m.group(1).trim();
            head.setTags(m.group(2).split(":"));
        }

        head.setTitle(title);

        return element;
    }

    /**
     * Removes empty lines before and after the content.
     * Called before every announcement.
     */
    private void trimContent(OrgHead head) {
        head.setContent(OrgStringUtils.trimLines(head.getContent()));
    }

    /**
     * Removes empty lines before and after the content.
     * Called before every announcement.
     */
    private void setTrimmedPreface(OrgFile file, String preface) {
        file.setPreface(OrgStringUtils.trimLines(preface));
    }
}
