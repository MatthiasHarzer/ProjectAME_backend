package server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

public class Util {
    private static String filePath = System.getProperty("user.dir") + "/server_log.txt";    //Logfile


    /**
     * Logged Nachrichten: Gibt diese mit Timestamp in der Konsole auf und schreibt sie in eine TXT-Datei
     *
     * @param s String to log
     */
    public static void log(String s) {
        try {
            File logFile = new File(filePath);  //Check ob die Datei existiert
            logFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Datum Formatierung
        LocalDateTime time = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        String formattedDate = time.format(formatter);


        String logmsg = "[" + formattedDate + "] " + s; //Pre- formatter

        System.out.println(logmsg); //Konsolen Ausgabe

        //Versuche den Log in eine Datei zuschreiben
        try {
            FileWriter logFileWriter = new FileWriter(filePath, true);
            logFileWriter.write(logmsg + "\n");
            logFileWriter.close();

        } catch (IOException e) {
            System.out.println("An error occurred writing to the log file!");
            e.printStackTrace();
        }
    }

    /**
     * Generiert einen Unique String (Einen String, der nicht in der Liste ref enthalten is)
     *
     * @param targetStringLength Länge
     * @param ref                Reference List (String darf nicht dort drin vorkommen)
     * @return unique String
     */
    public static String generateUniqueString(int targetStringLength, List<String> ref) {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'

        //Magic
        String generatedString;
        do {
            Random random = new Random();
            generatedString = random.ints(leftLimit, rightLimit + 1)
                    .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                    .limit(targetStringLength)
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString();
        } while (ref.contains(generatedString));
        return generatedString;
    }


}
