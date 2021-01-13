/*
 *  Copyright (C) 2021 Gwinnett County Experimental Aircraft Association
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.eaa690.aerie.service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Types;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.eaa690.aerie.model.Member;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Logs into EAA's roster management system, downloads the EAA 690 records as an Excel spreadsheet.
 * Then parses the spreadsheet for member details, and inserts (or updates) member data in a local MySQL database.
 */
public class RosterService {

    /**
     * Used for the CREATED BY and UPDATED BY columns in the database.
     */
    private final static String SCRIPT_NAME = "EAA690DoorDataUpdater";

    /**
     * eaachapters.org username variable.
     */
    private final static String USERNAME = "ctl00$txtID";

    /**
     * eaachapters.org password variable.
     */
    private final static String PASSWORD = "ctl00$txtPassword";

    /**
     * eaachapters.org signon button variable.
     */
    private final static String BUTTON = "ctl00$btnSignon";

    /**
     * eaachapters.org event target variable.
     */
    private final static String EVENT_TARGET = "__EVENTTARGET";

    /**
     * eaachapters.org event argument variable.
     */
    private final static String EVENT_ARGUMENT = "__EVENTARGUMENT";

    /**
     * eaachapters.org view state variable.
     */
    private final static String VIEW_STATE = "__VIEWSTATE";

    /**
     * eaachapters.org view state generator variable.
     */
    private final static String VIEW_STATE_GENERATOR = "__VIEWSTATEGENERATOR";

    /**
     * eaachapters.org event validation variable.
     */
    private final static String EVENT_VALIDATION = "__EVENTVALIDATION";

    /**
     * eaachapters.org Http User-Agent variable.
     */
    private final static String USER_AGENT = "User-Agent";

    /**
     * eaachapters.org Http Content-Type variable.
     */
    private final static String CONTENT_TYPE = "Content-Type";

    /**
     * eaachapters.org last focus variable.
     */
    private final static String LAST_FOCUS = "__LASTFOCUS";

    /**
     * eaachapters.org view state encrypted variable.
     */
    private final static String VIEW_STATE_ENCRYPTED = "__VIEWSTATEENCRYPTED";

    /**
     * eaachapters.org first name variable.
     */
    private final static String FIRST_NAME = "ctl00$ContentPlaceHolder1$txtFirstName";

    /**
     * eaachapters.org last name variable.
     */
    private final static String LAST_NAME = "ctl00$ContentPlaceHolder1$txtLastName";

    /**
     * eaachapters.org export button variable.
     */
    private final static String EXPORT_BUTTON = "ctl00$ContentPlaceHolder1$btnExport";

    /**
     * eaachapters.org status variable.
     */
    private final static String STATUS = "ctl00$ContentPlaceHolder1$ddlStatus";

    /**
     * eaachapters.org search member type variable.
     */
    private final static String SEARCH_MEMBER_TYPE = "ctl00$ContentPlaceHolder1$ddlSearchMemberType";

    /**
     * eaachapters.org current status variable.
     */
    private final static String CURRENT_STATUS = "ctl00$ContentPlaceHolder1$ddlCurrentStatus";

    /**
     * eaachapters.org row count variable.
     */
    private final static String ROW_COUNT = "ctl00$ContentPlaceHolder1$ddlRowCount";

    /**
     * eaachapters.org Http Accept variable.
     */
    private final static String ACCEPT = "Accept";

    /**
     * Base URL for EAA Chapters.
     */
    private final String EAA_CHAPTERS_SITE_BASE = "https://www.eaachapters.org";

    /**
     * Application properties.
     */
    private final Properties properties = new Properties();

    /**
     * HttpHeaders.
     */
    private final Map<String, String> headers = new HashMap<>();

    /**
     * Date formatter.
     */
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * Database query used to determine if a member is already stored.
     */
    private static final String CHECK_QUERY = "SELECT ID FROM MEMBER WHERE ROSTER_ID = ? ";

    /**
     * Database query used to insert a new member.
     */
    private static final String INSERT_QUERY = "INSERT INTO MEMBER (ROSTER_ID, RFID, FIRST_NAME, LAST_NAME, " +
            "EAA_NUMBER, EXPIRATION, CREATE_DATE, CREATED_BY) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * Database query used to update an existing member.
     */
    private static final String UPDATE_QUERY = "UPDATE MEMBER SET ROSTER_ID = ?, RFID = ?, FIRST_NAME = ?, " +
            "LAST_NAME = ?, EAA_NUMBER = ?, EXPIRATION = ?, UPDATE_DATE = ?, UPDATED_BY = ? WHERE ID = ? ";

    /**
     * Default constructor.
     */
    public RosterService() {
    }
    
    public void update() {
        try (InputStream input = new FileInputStream("../application.properties")) {
            properties.load(input);
        } catch (IOException e) {
            System.out.println("Unable to load application properties");
        }
        if (Boolean.valueOf(properties.getProperty("FETCH_DATA", "false"))) {
            getHttpHeaders();
            doLogin();
            getSearchMembersPage();
            fetchData();
        }
        if (Boolean.valueOf(properties.getProperty("UPDATE_DATA", "false"))) {
            updateDB(parseRecords(getData()));
        }
    }

    /**
     * Performs login to EAA's roster management system.
     */
    private void doLogin() {
        final String uriStr = EAA_CHAPTERS_SITE_BASE + "/main.aspx";
        final String requestBodyStr = buildLoginRequestBodyString();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uriStr))
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyStr));
        for (final String key : headers.keySet()) {
            builder.setHeader(key, headers.get(key));
        }
        final HttpRequest request = builder.build();

        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            System.out.println("[Login] Error: " + e.getMessage());
        }
    }


    /**
     * Performs login to EAA's roster management system.
     */
    private void getSearchMembersPage() {
        final String uriStr = EAA_CHAPTERS_SITE_BASE + "/searchmembers.aspx";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uriStr))
                .GET();
        for (final String key : headers.keySet()) {
            builder.setHeader(key, headers.get(key));
        }
        final HttpRequest request = builder.build();

        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            final Document doc = Jsoup.parse(response.body());
            final Element viewState = doc.getElementById(VIEW_STATE);
            headers.put(VIEW_STATE, viewState.attr("value"));
        } catch (Exception e) {
            System.out.println("[Search Page] Error: " + e.getMessage());
        }
    }

    /**
     * Fetch's data from EAA's roster management system.
     */
    private void fetchData() {
        final String uriStr = EAA_CHAPTERS_SITE_BASE + "/searchmembers.aspx";
        final String requestBodyStr = buildFetchDataRequestBodyString();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uriStr))
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyStr));
        headers.remove(VIEW_STATE);
        headers.put(ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        for (final String key : headers.keySet()) {
            builder.setHeader(key, headers.get(key));
        }
        final HttpRequest request = builder.build();

        PrintWriter out = null;
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            out = new PrintWriter(new BufferedWriter(new FileWriter("../EAAMembersSearch.xls")));
            out.println(response.body());
            out.flush();
        } catch (Exception e) {
            System.out.println("[FETCH] Error: " + e.getMessage());
        } finally {
            try { out.close(); } catch (Exception e) {}
        }
    }

    private void getHttpHeaders() {
        final HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(EAA_CHAPTERS_SITE_BASE + "/main.aspx")).GET().build();
        try {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            final HttpHeaders responseHeaders = response.headers();
            final String cookieStr = responseHeaders.firstValue("set-cookie").get();
            headers.put("cookie", cookieStr.substring(0, cookieStr.indexOf(";")));
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        headers.put(EVENT_TARGET, "");
        headers.put(EVENT_ARGUMENT, "");
        headers.put(VIEW_STATE, "/wEPDwUKMTY1NDU2MTA1MmRkuOlmdf9IlE5Upbw3feS5bMlNeitv2Tys6h3WSL105GQ=");
        headers.put(VIEW_STATE_GENERATOR, "202EA31B");
        headers.put(EVENT_VALIDATION, "/wEdAAaUkhCi8bB8A8YPK1mx/fN+Ob9NwfdsH6h5T4oBt2E/NC/PSAvxybIG70Gi7lMSo2Ha9mxIS56towErq28lcj7mn+o6oHBHkC8q81Z+42F7hK13DHQbwWPwDXbrtkgbgsBJaWfipkuZE5/MRRQAXrNwOiJp3YGlq4qKyVLK8XZVxQ==");
        headers.put(USERNAME, properties.getProperty("ROSTER_USER"));
        headers.put(PASSWORD, properties.getProperty("ROSTER_PASS"));
        headers.put(BUTTON, "Submit");
        headers.put(USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.121 Safari/537.36");
        headers.put(CONTENT_TYPE, "application/x-www-form-urlencoded");
        headers.put(EXPORT_BUTTON, "Results+To+Excel");
        headers.put(STATUS, "Active");
        headers.put(FIRST_NAME, "");
        headers.put(LAST_NAME, "");
        headers.put(SEARCH_MEMBER_TYPE, "");
        headers.put(CURRENT_STATUS, "");
        headers.put(ROW_COUNT, "");
        headers.put(VIEW_STATE_ENCRYPTED, "");
        headers.put(LAST_FOCUS, "");
    }

    private String buildLoginRequestBodyString() {
        final StringBuilder sb = new StringBuilder();
        final List<String> data = new ArrayList<>();
        data.add(EVENT_TARGET);
        data.add(EVENT_ARGUMENT);
        data.add(VIEW_STATE);
        data.add(VIEW_STATE_GENERATOR);
        data.add(EVENT_VALIDATION);
        data.add(USERNAME);
        data.add(PASSWORD);
        data.add(BUTTON);
        for (final String key : headers.keySet()) {
            if (data.contains(key)) {
                if (sb.length() > 0) {
                    sb.append("&");
                }
                if (USERNAME.equals(key) || PASSWORD.equals(key) || BUTTON.equals(key)) {
                    sb.append(key.replaceAll("\\$", "%24"));
                } else {
                    sb.append(key);
                }
                sb.append("=");
                if (VIEW_STATE.equals(key) || EVENT_VALIDATION.equals(key)) {
                    sb.append(headers.get(key)
                            .replaceAll("/", "%2F")
                            .replaceAll("=", "%3D")
                            .replaceAll("\\+", "%2B"));
                } else {
                    sb.append(headers.get(key));
                }
            }
        }
        return sb.toString();
    }

    private String buildFetchDataRequestBodyString() {
        final StringBuilder sb = new StringBuilder();
        final List<String> data = new ArrayList<>();
        data.add(EVENT_TARGET);
        data.add(EVENT_ARGUMENT);
        data.add(LAST_FOCUS);
        data.add(VIEW_STATE);
        data.add(VIEW_STATE_GENERATOR);
        data.add(VIEW_STATE_ENCRYPTED);
        data.add(FIRST_NAME);
        data.add(LAST_NAME);
        data.add(EXPORT_BUTTON);
        data.add(STATUS);
        data.add(SEARCH_MEMBER_TYPE);
        data.add(CURRENT_STATUS);
        data.add(ROW_COUNT);
        for (final String key : headers.keySet()) {
            if (data.contains(key)) {
                if (sb.length() > 0) {
                    sb.append("&");
                }
                if (FIRST_NAME.equals(key) ||
                        LAST_NAME.equals(key) ||
                        EXPORT_BUTTON.equals(key) ||
                        STATUS.equals(key) ||
                        SEARCH_MEMBER_TYPE.equals(key) ||
                        CURRENT_STATUS.equals(key) ||
                        ROW_COUNT.equals(key)) {
                    sb.append(key.replaceAll("\\$", "%24"));
                } else {
                    sb.append(key);
                }
                sb.append("=");
                if (VIEW_STATE.equals(key) || EVENT_VALIDATION.equals(key)) {
                    sb.append(headers.get(key)
                            .replaceAll("/", "%2F")
                            .replaceAll("=", "%3D")
                            .replaceAll("\\+", "%2B"));
                } else {
                    sb.append(headers.get(key));
                }
            }
        }
        return sb.toString();
    }

    /**
     * Reads in an Excel spreadsheet.
     *
     * @return Excel spreadsheet
     */
    private String getData() {
        final StringBuilder sb = new StringBuilder();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader("../EAAMembersSearch.xls"));
            while (in.ready()) {
                sb.append(in.readLine());
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            try { in.close(); } catch (Exception e) {}
        }
        return sb.toString();
    }

    /**
     * Parses select values from Excel spreadsheet.
     *
     * @param excel spreadsheet
     * @return list of parsed values
     */
    private List<Member> parseRecords(String excel) {
        final List<Member> records = new ArrayList<>();
        final Document doc = Jsoup.parse(excel);
        final Elements tableRecords = doc.getElementsByTag("tr");
        int rowCount = 0;
        for (Element tr : tableRecords) {
            if (rowCount > 0) {
                try {
                    final Elements columns = tr.getElementsByTag("td");
                    int columnCount = 0;
                    final Member member = new Member();
                    for (Element column : columns) {
                        if (columnCount == 0) {
                            member.setRosterId(Long.parseLong(column.text().trim()));
                        }
                        if (columnCount == 2) {
                            member.setRfid(column.text().trim());
                        }
                        if (columnCount == 3) {
                            member.setFirstName(column.text().trim());
                        }
                        if (columnCount == 4) {
                            member.setLastName(column.text().trim());
                        }
                        if (columnCount == 18) {
                            member.setEaaNumber(column.text().trim());
                        }
                        if (columnCount == 21) {
                            member.setExpiration(SDF.parse(column.text().trim()));
                        }
                        columnCount++;
                    }
                    records.add(member);
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
            rowCount++;
        }
        return records;
    }

    /**
     * Updates the local MySQL database with the provided member record data.
     *
     * @param members list of members
     */
    private void updateDB(List<Member> members) {
        final Connection con = getDBConnection();
        PreparedStatement checkPS = null;
        PreparedStatement insertPS = null;
        PreparedStatement updatePS = null;
        try {
            checkPS = con.prepareStatement(CHECK_QUERY);
            insertPS = con.prepareStatement(INSERT_QUERY);
            updatePS = con.prepareStatement(UPDATE_QUERY);
            for (Member member : members) {
                if (member != null) {
                    checkPS.setLong(1, member.getRosterId());
                    final ResultSet checkRS = checkPS.executeQuery();
                    if (checkRS.next()) {
                        member.setId(checkRS.getLong(1));
                        doUpdate(updatePS, member);
                    } else {
                        doInsert(insertPS, member);
                    }
                    checkRS.close();
                }
            }
        } catch (SQLException e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            try { checkPS.close(); } catch (Exception e) {}
            try { updatePS.close(); } catch (Exception e) {}
            try { insertPS.close(); } catch (Exception e) {}
            try { con.close(); } catch (Exception e) {}
        }
    }

    /**
     * Performs an insert of a new member.
     *
     * @param insertPS insert prepared statement
     * @param member to be inserted
     * @throws SQLException when a database error occurs
     */
    private void doInsert(PreparedStatement insertPS, Member member) throws SQLException {
        insertPS.setLong(1, member.getRosterId());
        if (member.getRfid() != null) {
            insertPS.setString(2, member.getRfid());
        } else {
            insertPS.setNull(2, Types.VARCHAR);
        }
        if (member.getFirstName() != null) {
            insertPS.setString(3, member.getFirstName());
        } else {
            insertPS.setNull(3, Types.VARCHAR);
        }
        if (member.getLastName() != null) {
            insertPS.setString(4, member.getLastName());
        } else {
            insertPS.setNull(4, Types.VARCHAR);
        }
        if (member.getEaaNumber() != null) {
            insertPS.setString(5, member.getEaaNumber());
        } else {
            insertPS.setNull(5, Types.VARCHAR);
        }
        if (member.getExpiration() != null) {
            insertPS.setTimestamp(6, new Timestamp(member.getExpiration().getTime()));
        } else {
            insertPS.setNull(6, Types.TIMESTAMP);
        }
        insertPS.setTimestamp(7, new Timestamp((new Date()).getTime()));
        insertPS.setString(8, SCRIPT_NAME);
        insertPS.executeUpdate();
    }

    /**
     * Performs an update of a new member.
     *
     * @param updatePS update prepared statement
     * @param member to be updated
     * @throws SQLException when a database error occurs
     */
    private void doUpdate(PreparedStatement updatePS, Member member) throws SQLException {
        updatePS.setLong(1, member.getRosterId());
        if (member.getRfid() != null) {
            updatePS.setString(2, member.getRfid());
        } else {
            updatePS.setNull(2, Types.VARCHAR);
        }
        if (member.getFirstName() != null) {
            updatePS.setString(3, member.getFirstName());
        } else {
            updatePS.setNull(3, Types.VARCHAR);
        }
        if (member.getLastName() != null) {
            updatePS.setString(4, member.getLastName());
        } else {
            updatePS.setNull(4, Types.VARCHAR);
        }
        if (member.getEaaNumber() != null) {
            updatePS.setString(5, member.getEaaNumber());
        } else {
            updatePS.setNull(5, Types.VARCHAR);
        }
        if (member.getExpiration() != null) {
            updatePS.setTimestamp(6, new Timestamp(member.getExpiration().getTime()));
        } else {
            updatePS.setNull(6, Types.TIMESTAMP);
        }
        updatePS.setTimestamp(7, new Timestamp((new Date()).getTime()));
        updatePS.setString(8, SCRIPT_NAME);
        updatePS.setLong(9, member.getId());
        updatePS.executeUpdate();
    }

    /**
     * Gets a connection to the local MySQL database.
     *
     * @return Connection
     */
    private Connection getDBConnection() {
        Connection con = null;
        try {
            Class.forName("com.mysql.jdbc.Driver");
            con = DriverManager.getConnection(
                    properties.getProperty("DB_URL"),
                    properties.getProperty("DB_USER"),
                    properties.getProperty("DB_PASS"));
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return con;
    }
}