package fr.orgpro.api.orgzly.parser;


import fr.orgpro.api.orgzly.OrgFile;

import java.io.IOException;

public interface OrgSaxyParserListener {
    /**
     * Called for each new heading found.
     *
     * @param node Node in list.
     * @throws IOException Exception throws on error
     */
    void onHead(OrgNodeInList node) throws IOException;

    /**
     * Called last, after everything has been parsed.
     *
     * @param file File
     * @throws IOException Exception throws on error
     */
    void onFile(OrgFile file) throws IOException;
}
