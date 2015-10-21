package com.sealionsoftware.googledistance;

import com.google.maps.DistanceMatrixApi;
import com.google.maps.DistanceMatrixApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DistanceMatrix;
import com.google.maps.model.DistanceMatrixElement;
import com.google.maps.model.DistanceMatrixRow;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.LogManager;

public class Client {

    private static final String KEY_PROPERTY = "google.api.key";

    static {
        LogManager.getLogManager().reset();
    }

    public static void main(String... args) throws Exception {

        File directory = new File(System.getProperty("user.home") + File.separator + ".googledistanceclient");
        if (!directory.exists()) {
            directory.mkdirs();
        }

        Properties properties = new Properties();
        File propfile = new File(directory, "runtime.properties");
        if (propfile.exists()) {
            properties.load(new FileReader(propfile));
        } else {
            propfile.createNewFile();
        }

        Set<String> existingAddresses = new HashSet<>();
        File addressfile = new File(directory, "addresses.txt");
        if (addressfile.exists()) {
            BufferedReader reader = new BufferedReader(new FileReader(addressfile));
            String address;
            while ((address = reader.readLine()) != null){
                existingAddresses.add(address);
            }
        } else {
            addressfile.createNewFile();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {

            String key = readKey(reader, properties.getProperty(KEY_PROPERTY));
            properties.put(KEY_PROPERTY, key);
            try (FileWriter writer = new FileWriter(propfile)) {
                properties.store(writer, "Last used properties for the google distance client");
            }

            GeoApiContext context = new GeoApiContext()
                    .setApiKey(key);

            String[] newAddresses = readAddresses(reader);

            if (existingAddresses.isEmpty()){
                generateMatrix(context, newAddresses, newAddresses);
            } else {

                for (String requestedAddress : newAddresses) if (existingAddresses.contains(requestedAddress)) {
                    throw new RuntimeException("The address " + requestedAddress + " is already in the address file");
                }

                String[] existingAddressesArray = existingAddresses.toArray(new String[existingAddresses.size()]);
                String[] combinedAddressArray = new String[newAddresses.length + existingAddressesArray.length];
                System.arraycopy(newAddresses, 0, combinedAddressArray, 0, newAddresses.length);
                System.arraycopy(existingAddressesArray, 0, combinedAddressArray, newAddresses.length, existingAddressesArray.length);

                generateMatrix(context, newAddresses, combinedAddressArray);
                generateMatrix(context, existingAddressesArray, newAddresses);
            }

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(addressfile, true))){
                for (String newAddress : newAddresses){
                    bw.write(newAddress);
                    bw.newLine();
                }
            }

        } catch (Exception e) {
            System.err.println("The request could not be completed: ");
            e.printStackTrace();
        }
    }

    private static void generateMatrix(GeoApiContext context, String[] from, String[] to) throws Exception {
        DistanceMatrixApiRequest request = DistanceMatrixApi.getDistanceMatrix(context, from, to);

        DistanceMatrix result = request.await();
        int i = 0;
        for (DistanceMatrixRow row : result.rows) {
            System.out.println("From " + from[i] + " (" + result.originAddresses[i++] + ")");
            int j = 0;
            for (DistanceMatrixElement element : row.elements) {
                System.out.print("\tTo  " + to[j++] + ": ");
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
