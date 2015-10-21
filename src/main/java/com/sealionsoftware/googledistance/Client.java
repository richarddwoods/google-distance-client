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
    private static final String QPS_PROPERTY = "google.api.queriesPerSecond";
    private static final String THROTTLE_PROPERTY = "google.api.throttleInterval";


    public static void main(String... args) throws Exception {

        LogManager.getLogManager().reset();

        Client client = new Client();
        client.run();
    }

    public void run() throws Exception {

        File directory = getDirectory();

        Properties properties = new Properties();
        File propertiesFile = loadPropertiesFile(directory, properties);
        int queriesPerSecond = getProperty(QPS_PROPERTY, properties, 1);
        int throttleProperty = getProperty(THROTTLE_PROPERTY, properties, 10000);

        Set<String> existingAddresses = new HashSet<>();
        File addressFile = loadAddressFile(directory, existingAddresses);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {

            String key = readAndStoreKey(reader, properties, propertiesFile);

            String[] newAddresses = readAddresses(reader);
            System.out.println("Calling webservice... please wait this may take some time");
            System.out.println();

            GeoApiContext context = new GeoApiContext()
                    .setApiKey(key)
                    .setQueryRateLimit(queriesPerSecond, throttleProperty);
            if (existingAddresses.isEmpty()){
                generateMatrix(context, newAddresses, newAddresses);
            } else {
                generateMatrixExtension(context, newAddresses, existingAddresses);
            }

            updateAddressFile(addressFile, newAddresses);

        } catch (Exception e) {
            System.err.println("The request could not be completed: ");
            e.printStackTrace();
        }
    }

    private int getProperty(String name, Properties properties, int defaultValue) {
        String value = properties.getProperty(name);
        if (value == null){
            properties.setProperty(name, Integer.toString(defaultValue));
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private String readAndStoreKey(BufferedReader reader, Properties properties, File propertiesFile) throws Exception {
        String lastEnteredKey = properties.getProperty(KEY_PROPERTY);
        String key = readKey(reader, lastEnteredKey);
        properties.put(KEY_PROPERTY, key);
        try (FileWriter writer = new FileWriter(propertiesFile)) {
            properties.store(writer, "Last used properties for the google distance client");
        }
        return key;
    }

    private void updateAddressFile(File addressFile, String[] newAddresses) throws Exception {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(addressFile, true))){
            for (String newAddress : newAddresses){
                bw.write(newAddress);
                bw.newLine();
            }
        }
    }

    private void generateMatrixExtension(GeoApiContext context, String[] newAddresses, Set<String> existingAddresses) throws Exception {
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

    private File loadAddressFile(File directory, Set<String> existingAddresses) throws Exception {
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
        return addressfile;
    }

    private File loadPropertiesFile(File directory, Properties properties) throws Exception{
        File propfile = new File(directory, "runtime.properties");
        if (propfile.exists()) {
            properties.load(new FileReader(propfile));
        } else {
            propfile.createNewFile();
        }
        return propfile;
    }

    private File getDirectory() {
        File directory = new File(System.getProperty("user.home") + File.separator + ".googledistanceclient");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    private void generateMatrix(GeoApiContext context, String[] from, String[] to) throws Exception {
        DistanceMatrixApiRequest request = DistanceMatrixApi.getDistanceMatrix(context, from, to);

        DistanceMatrix result = request.await();
        int i = 0;
        for (DistanceMatrixRow row : result.rows) {
            int j = 0;
            for (DistanceMatrixElement element : row.elements) {
                if (i != j) switch (element.status) {
                    case NOT_FOUND: {
                        System.err.println("Route not found");
                        break;
                    }
                    case OK: {
                        System.out.println(from[i] + "," + to[j] + "," + element.distance.inMeters + "," + element.duration.inSeconds);
                    }
                }
                j++;
            }
            i++;
        }
    }

    private String readKey(BufferedReader reader, String defaultKey) throws Exception {
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

    private String[] readAddresses(BufferedReader reader) throws Exception {
        System.out.println("Please enter the addresses to cross check as separate lines, followed by an empty line");
        List<String> input = new ArrayList<>();

        String line;
        while (!(line = reader.readLine()).isEmpty()) {
            input.add(line);
        }

        return input.toArray(new String[input.size()]);
    }

}
