package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.exception.DictConnectionException;
import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;
import ca.ubc.cs317.dict.util.DictStringParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    private Map<String, Database> databaseMap = new LinkedHashMap<String, Database>();

    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {

            try {
                    this.socket = new Socket(host, port);
                    this.output =
                            new PrintWriter(socket.getOutputStream(), true);
                    this.input =
                            new BufferedReader(
                                    new InputStreamReader((socket.getInputStream())));

                    // Gets the status code of the dictionary command
                    Status result = Status.readStatus(input);

                    // Check if the connection was successful, otherwise close the connection
                    if(result.getStatusCode() != 220)
                        close();

            } catch (Exception e) {
                throw new DictConnectionException();
            }
    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
      this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    public synchronized void close() {

        // Close connection with the server
        try {
            output.println("QUIT \r\n");
            socket.close();
        } catch (Exception e) {
        }
    }


    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();
        getDatabaseList(); // Ensure the list of databases has been populated

        try {
            // Store a word or a phrase to be read by the server
            String userInput = '"' + word + '"';

            // Send a query to the server
            output.write("DEFINE " + database.getName() + " " + userInput + " "+ "\r\n");
            output.flush();
            String result = input.readLine();
            String[] defList = DictStringParser.splitAtoms(result);

            // If no definitions were found in any databases, return an empty set
            if(defList[0].equals("552")) {
                return set;
            }

            // Check if the proper dictionary commands are returned, then parse for definitions of given word
            if(defList[0].equals("150")) {
                // Check if there are no more definitions left to parse
                while (!(result = input.readLine()).substring(0,3).equals("250")){
                    String[] validLine = DictStringParser.splitAtoms(result);

                    // Check if beginning of a definition, create a new definition
                    if(validLine[0].equals("151")) {
                        Database db = new Database(validLine[2],validLine[3]);
                        Definition newDef = new Definition(word, db);
                        String def = "";

                        // Append entire definition into the empty string
                        while(!(result = input.readLine()).equals(".")) {
                            def = def.concat(result + "\r\n");
                        }

                        newDef.setDefinition(def);
                        set.add(newDef);
                    }
                }
                return set;

            } else {
                throw new DictConnectionException();
            }

        } catch (Exception e) {
            throw new DictConnectionException();
        }
    }

    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        try {
            // Store a word or a phrase to be read by the server
            String userInput = '"' + word + '"';

            // Send a query to the server
            output.write("MATCH " + database.getName() + " " + strategy.getName() + " " + userInput + " " + "\r\n");
            output.flush();
            String result = input.readLine();
            String[] matchList = DictStringParser.splitAtoms(result);

            // If no definitions were found in any databases, return an empty set
            if (matchList[0].equals("552")){
                return set;
            }
            //  Parse a matching strategy
            if(matchList[0].equals("152")) {
                while(!(result = input.readLine()).equals(".")) {
                    String[] toParse = DictStringParser.splitAtoms(result);
                    set.add(toParse[1]);
                }

                // Empty the buffer
                result = input.readLine();
                return set;

            } else {
                throw new DictConnectionException();
            }

        } catch (Exception e) {
            throw new DictConnectionException();
        }
    }

    /** Requests and retrieves a list of all valid databases used in the server. In addition to returning the list, this
     * method also updates the local databaseMap field, which contains a mapping from database name to Database object,
     * to be used by other methods (e.g., getDefinitionMap) to return a Database object based on the name.
     *
     * @return A collection of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Database> getDatabaseList() throws DictConnectionException {

        if (!databaseMap.isEmpty()) return databaseMap.values();


        try{
            // Send a query to the server
            output.write("SHOW DB \r\n");
            output.flush();
            String result = input.readLine();
            String[] dictList = DictStringParser.splitAtoms(result);

            // Check if valid dictionary command, then parse for a database
            if(dictList[0].equals("110")) {
                while(!(result = input.readLine()).equals(".")) {
                    String[] toParse = DictStringParser.splitAtoms(result);
                    Database db = new Database(toParse[0], toParse[1]);
                    databaseMap.put(toParse[0],db);
                }

                // Empty the buffer
                result = input.readLine();
                return databaseMap.values();

            } else {
                throw new DictConnectionException();
            }

        } catch(Exception e) {
            throw new DictConnectionException(e.getMessage());
        }
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        try {
            // Send a query to the server
            output.write("SHOW STRAT \r\n");
            output.flush();
            String result = input.readLine();
            String[] Stratlist = DictStringParser.splitAtoms(result);

            // Check if valid dictionary command, then parse for a strategy
            if(Stratlist[0].equals("111")) {
                while(!(result = input.readLine()).equals(".")) {
                    String[] toParse = DictStringParser.splitAtoms(result);
                    MatchingStrategy ms = new MatchingStrategy(toParse[0],toParse[1]);
                    set.add(ms);
                }

                // Empty the buffer
                result = input.readLine();
                return set;

            } else {
                throw new DictConnectionException();
            }

        } catch (Exception e) {
            throw new DictConnectionException();
        }
    }
}
