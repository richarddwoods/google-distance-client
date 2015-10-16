package com.sealionsoftware.googledistance;

import com.google.maps.DistanceMatrixApi;
import com.google.maps.DistanceMatrixApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DistanceMatrix;
import com.google.maps.model.DistanceMatrixElement;
import com.google.maps.model.DistanceMatrixRow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Client {

    private static final String KEY_PROPERTY = "google.api.key";

    public static void main(String... args) throws Exception {

        Properties properties = new Properties();
        File directory = new File(System.getProperty("user.home") + File.separator + ".googledistanceclient");
        File propfile = new File(directory, "runtime.properties");
        if (!directory.exists()) {
            directory.mkdirs();
        } else {
            if (propfile.exists()) {
                properties.load(new FileReader(propfile));
            } else {
                propfile.createNewFile();
            }
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {

            String key = readKey(reader, properties.getProperty(KEY_PROPERTY));
            properties.put(KEY_PROPERTY, key);
            try (FileWriter writer = new FileWriter(propfile)) {
                properties.store(writer, "Last used properties for the google distance client");
            }

            GeoApiContext context = new GeoApiContext()
                    .setApiKey(key);

            String[] addresses = readAddresses(reader);
            DistanceMatrixApiRequest request = DistanceMatrixApi.getDistanceMatrix(context, addresses, addresses);

            DistanceMatrix result = request.await();
            int i = 0;
            for (DistanceMatrixRow row : result.rows) {
                System.out.println("From " + addresses[i] + " (" + result.originAddresses[i++] + ")");
                int j = 0;
                for (DistanceMatrixElement element : row.elements) {
                    System.out.print("\tTo  " + addresses[j++] + ": ");
                    switch (element.status) {
                        case NOT_FOUND: {
                            System.err.println("Route not found");
                            break;
                        }
                        case OK: {
                            System.out.println(element.distance.humanReadable + ", " + element.duration.humanReadable);
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("The request could not be completed: ");
            e.printStackTrace();
        }
    }

    private static String readKey(BufferedReader reader, String defaultKey) throws Exception {
        String line;
        String keyPrompt = "Please enter the key to use";
        if (defaultKey != null){
            keyPrompt += " [" + defaultKey + "]";
        }
        System.out.println(keyPrompt);
        while ((line = reader.readLine()) != null) {
            if (!line.isEmpty()) {
                return line;
            }
            if (defaultKey != null) {
                return defaultKey;
            }
            System.out.println(keyPrompt);
        }
        throw new RuntimeException("Could not read key from user");
    }

    private static String[] readAddresses(BufferedReader reader) throws Exception {
        System.out.println("Please enter the addresses to cross check as separate lines, followed by an empty line");
        List<String> input = new ArrayList<>();

        String line;
        while (!(line = reader.readLine()).isEmpty()) {
            input.add(line);
        }

        return input.toArray(new String[input.size()]);
    }

}
