JFLAGS = -g
JC = javac
all: Dictionary.jar

.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

Dictionary.jar: ca/ubc/cs317/dict/ui/DictionaryMain.class
	jar cvfe Dictionary.jar ca.ubc.cs317.dict.ui.DictionaryMain ca/

run: Dictionary.jar
	java -jar Dictionary.jar

clean:
	find . -iname '*.class' -delete
	-rm -rf  Dictionary.jar
