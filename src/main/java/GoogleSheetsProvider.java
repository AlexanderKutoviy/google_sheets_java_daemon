import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GoogleSheetsProvider {

    private static final String APPLICATION_NAME = "Google Sheets API Java Quickstart";
    private static final java.io.File DATA_STORE_DIR = new java.io.File(
            System.getProperty("user.home"), ".credentials/sheets.googleapis.com-java-quickstart1");
    private static FileDataStoreFactory DATA_STORE_FACTORY;
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static HttpTransport HTTP_TRANSPORT;
    private static final List<String> SCOPES = Arrays.asList(SheetsScopes.SPREADSHEETS, SheetsScopes.DRIVE);

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private Thread thread;
    private final Object wakeMeUp = new Object();
    private Connection connection;

    public GoogleSheetsProvider(Connection c) {
        this.connection = c;
        start();
    }


    public synchronized void start() {
        if (thread == null) {
            thread = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            tick();
                        } catch (Exception e) {
                            e.printStackTrace();
                            break;
                        } catch (Throwable e) {
                            try {
                                synchronized (wakeMeUp) {
                                    wakeMeUp.wait(10000);
                                }
                            } catch (InterruptedException e1) {
                                e.printStackTrace();
                                break;
                            }
                        }
                    }
                }
            };
            thread.setDaemon(true);
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void stop() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public void tick() throws Exception {
        System.out.println("tick");
        Statement stmt = connection.createStatement();
        String sql = "SELECT * FROM students WHERE synced=1;";
        ResultSet rs = stmt.executeQuery(sql);
        System.out.println("check RS");
        while (rs.next()) {
            synchronized (wakeMeUp) {
                wakeMeUp.wait(10000);
            }
            System.out.println("RS has next");
            System.out.println("RS = " + rs.getString("name"));
            write();
        }
        System.out.println("checked RS");
        rs.close();
        stmt.close();
    }

    public static Credential authorize() throws IOException {
        // Load client secrets.
        InputStream in = GoogleSheetsProvider.class.getResourceAsStream("/client_secret.json");
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                        .setDataStoreFactory(DATA_STORE_FACTORY)
                        .setAccessType("offline")
                        .setApprovalPrompt("auto")
                        .build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
        System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }

    public Sheets getSheetsService() throws IOException {
        Credential credential = authorize();
        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public void write() {
        Sheets service = null;
        System.out.println("WRITE");
        try {
            service = getSheetsService();

            String spreadsheetId = "10tQ5MHGGiDcoqTdEV2Pupy6wh171wyRbjSH-eXbaKC8";
            String range = "B3";
            List<Request> requests = new ArrayList<Request>();
            requests.add(new Request()
                    .setFindReplace(new FindReplaceRequest()
                            .setFind("0")
                            .setReplacement("1")
                            .setAllSheets(true)));
            BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest().setRequests(requests);
            BatchUpdateSpreadsheetResponse response = service.spreadsheets().batchUpdate(spreadsheetId, body).execute();

            FindReplaceResponse findReplaceResponse = response.getReplies().get(0).getFindReplace();
            System.out.printf("%d replacements made.", findReplaceResponse.getOccurrencesChanged());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeToCell() {
        Sheets service = null;
        System.out.println("WRITE");
        try {
            service = getSheetsService();

            String spreadsheetId = "10tQ5MHGGiDcoqTdEV2Pupy6wh171wyRbjSH-eXbaKC8";
            String range = "B3";
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void readTest() throws IOException {
        // Build a new authorized API client service.
        Sheets service = getSheetsService();

        // Prints the names and majors of students in a sample spreadsheet:
        // https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit
        String spreadsheetId = "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms";
        String range = "Class Data!A2:E";
        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        List<List<Object>> values = response.getValues();
        if (values == null || values.size() == 0) {
            System.out.println("No data found.");
        } else {
            System.out.println("Name, Major");
            for (List row : values) {
                // Print columns A and E, which correspond to indices 0 and 4.
                System.out.printf("%s, %s\n", row.get(0), row.get(4));
            }
        }
    }

}