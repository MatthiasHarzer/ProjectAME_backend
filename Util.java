package server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

public class Util {
    private static String filePath = System.getProperty("user.dir");


    public static void log(String s) {
        try {
            File logFile = new File(filePath + "\\server_log.txt");
            logFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        LocalDateTime time = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        String formattedDate = time.format(formatter);


        String logmsg = "[" + formattedDate + "] " + s;

        System.out.println(logmsg);

        try {
            FileWriter logFileWriter = new FileWriter(filePath + "\\server_log.txt", true);
            logFileWriter.write(logmsg + "\n");
            logFileWriter.close();

        } catch (IOException e) {
            System.out.println("An error occurred writing to the log file!");
            e.printStackTrace();
        }
    }

    public static String generateUniqueString(int targetStringLength, List<String> ref) {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'

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

    public static String generateRandomString(int targetStringLength) {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'

        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

}
