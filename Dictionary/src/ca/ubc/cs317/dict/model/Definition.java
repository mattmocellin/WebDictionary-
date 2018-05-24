package ca.ubc.cs317.dict.model;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class Definition {

    private String word;
    private Database database;
    private String definition;

    public Definition(String word, Database database) {
        this.word = word;
        this.database = database;
    }

    public String getWord() {
        return word;
    }

    public Database getDatabase() {
        return database;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public void appendDefinition(String definition) {
        if (this.definition == null)
            this.definition = definition;
        else if (definition != null)
            this.definition += System.lineSeparator() + definition;
    }

}
