/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.tests.nightlybenchmarks;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.ZipInputStream;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.lucene.util.TestUtil;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import au.com.bytecode.opencsv.CSVReader;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

enum MessageType {
  YELLOW_TEXT, WHITE_TEXT, GREEN_TEXT, RED_TEXT, BLUE_TEXT, BLACK_TEXT, PURPLE_TEXT, CYAN_TEXT
};

/**
 * This class provides utility methods for the package.
 * @author Vivek Narang
 *
 */
public class Util {

  public final static Logger logger = Logger.getLogger(Util.class);

  public static String WORK_DIRECTORY = System.getProperty("user.dir");
  public static String DNAME = "SolrNightlyBenchmarksWorkDirectory";
  public static String BASE_DIR = WORK_DIRECTORY + File.separator + DNAME + File.separator;
  public static String RUN_DIR = BASE_DIR + "RunDirectory" + File.separator;
  public static String DOWNLOAD_DIR = BASE_DIR + "Download" + File.separator;
  public static String ZOOKEEPER_DOWNLOAD_URL = "http://www.us.apache.org/dist/zookeeper/";
  public static String ZOOKEEPER_RELEASE = "3.4.14";
  public static String ZOOKEEPER_DIR = RUN_DIR;
  public static String SOLR_DIR = RUN_DIR;
  public static String ZOOKEEPER_IP = "127.0.0.1";
  public static String ZOOKEEPER_PORT = "2181";
  public static String LUCENE_SOLR_REPOSITORY_URL = "https://github.com/apache/lucene-solr";
  public static String GIT_REPOSITORY_PATH;
  public static String SOLR_PACKAGE_DIR;
  public static String SOLR_PACKAGE_DIR_LOCATION;
  public static String COMMIT_ID;
  public static String TEST_ID = UUID.randomUUID().toString();
  public static String TEST_TIME = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date());
  public static String METRIC_ESTIMATION_PERIOD = "1000";
  public static String QUERY_THREAD_COUNT_FIRST = "";
  public static String QUERY_THREAD_COUNT_SECOND = "";
  public static String QUERY_THREAD_COUNT_THIRD = "";
  public static String QUERY_THREAD_COUNT_FOURTH = "";
  public static String TEST_DATA_DIRECTORY = "";
  public static String ONEM_TEST_DATA = "";
  public static String NUMERIC_QUERY_TERM_DATA = "";
  public static String NUMERIC_QUERY_PAIR_DATA = "";
  public static String NUMERIC_QUERY_AND_OR_DATA = "";
  public static String NUMERIC_SORTED_QUERY_PAIR_DATA = "";
  public static String TEXT_TERM_DATA = "";
  public static String TEXT_PHRASE_DATA = "";
  public static String HIGHLIGHT_TERM_DATA = "";
  public static String TEST_DATA_STORE_LOCATION = "";
  public static String RANGE_FACET_DATA = "";
  public static String TEST_DATA_ARCHIVE_LOCATION = "";
  public static long NUMBER_OF_QUERIES_TO_RUN = 1000;
  public static long NUMBER_OF_QUERIES_TO_RUN_FOR_FACETING = 1000;
  public static long TEST_WITH_NUMBER_OF_DOCUMENTS = 100000;
  public static boolean USE_COLORED_TEXT_ON_CONSOLE = true;
  public static boolean SILENT = false;
  public static List<String> argsList;


  /**
   * A method used for invoking a process with specific parameters.
   * 
   * @param command
   * @param workingDirectoryPath
   * @return
   * @throws Exception 
   */
  public static int execute(String command, String workingDirectoryPath) throws Exception {
    logger.debug("Executing: " + command);
    logger.debug("Working dir: " + workingDirectoryPath);
    File workingDirectory = new File(workingDirectoryPath);

    workingDirectory.setExecutable(true);

    Runtime rt = Runtime.getRuntime();
    Process proc = null;
    ProcessStreamReader processErrorStream = null;
    ProcessStreamReader processOutputStream = null;

    try {
      //      proc = rt.exec(command, new String[] {"JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk"}, workingDirectory);
      proc = rt.exec(command, new String[] {"JAVA_HOME=/usr/lib/jvm/java-11-openjdk"}, workingDirectory);
      processErrorStream = new ProcessStreamReader(proc.getErrorStream(), "ERROR");
      processOutputStream = new ProcessStreamReader(proc.getInputStream(), "OUTPUT");

      processErrorStream.start();
      processOutputStream.start();
      proc.waitFor();
      return proc.exitValue();
    } catch (Exception e) {
      logger.error(e.getMessage());
      throw new Exception(e.getMessage());
    }
  }

  public static Map map(Object... params) {
    LinkedHashMap ret = new LinkedHashMap();
    for (int i=0; i<params.length; i+=2) {
      Object o = ret.put(params[i], params[i+1]);
      // TODO: handle multi-valued map?
    }
    return ret;
  }

  /**
   * A method for printing output on a single line.
   * 
   * @param message
   */
  public static void postMessageOnLine(String message) {
    if (!SILENT) {
      System.out.print(message);
    }
  }

  /**
   * A method used for get an available free port for running the
   * solr/zookeeper node on.
   * 
   * @return int
   * @throws Exception 
   */
  public static int getFreePort() throws Exception {

    int port = ThreadLocalRandom.current().nextInt(10000, 60000);
    logger.debug("Looking for a free port ... Checking availability of port number: " + port);
    ServerSocket serverSocket = null;
    DatagramSocket datagramSocket = null;
    try {
      serverSocket = new ServerSocket(port);
      serverSocket.setReuseAddress(true);
      datagramSocket = new DatagramSocket(port);
      datagramSocket.setReuseAddress(true);
      logger.debug("Port " + port + " is free to use. Using this port !!");
      return port;
    } catch (IOException e) {
    } finally {
      if (datagramSocket != null) {
        datagramSocket.close();
      }

      if (serverSocket != null) {
        try {
          serverSocket.close();
        } catch (IOException e) {
          logger.error(e.getMessage());
          throw new IOException(e.getMessage());
        }
      }
      // Marking for GC
      serverSocket = null;
      datagramSocket = null;
    }

    logger.debug("Port " + port + " looks occupied trying another port number ... ");
    return getFreePort();
  }

  /**
   * 
   * @param plaintext
   * @return String
   * @throws Exception 
   */
  static public String md5(String plaintext) throws Exception {
    MessageDigest m;
    String hashtext = null;
    try {
      m = MessageDigest.getInstance("MD5");
      m.reset();
      m.update(plaintext.getBytes());
      byte[] digest = m.digest();
      BigInteger bigInt = new BigInteger(1, digest);
      hashtext = bigInt.toString(16);
      // Now we need to zero pad it if you actually want the full 32
      // chars.
      while (hashtext.length() < 32) {
        hashtext = "0" + hashtext;
      }
    } catch (NoSuchAlgorithmException e) {
      logger.error(e.getMessage());
      throw new Exception(e.getMessage());
    }
    return hashtext;
  }

  /**
   * A metod used for extracting files from an archive.
   * 
   * @param zipIn
   * @param filePath
   * @throws Exception 
   */
  public static void extractFile(ZipInputStream zipIn, String filePath) throws Exception {

    BufferedOutputStream bos = null;
    try {

      bos = new BufferedOutputStream(new FileOutputStream(filePath));
      byte[] bytesIn = new byte[4096];
      int read = 0;
      while ((read = zipIn.read(bytesIn)) != -1) {
        bos.write(bytesIn, 0, read);
      }
      bos.close();
    } catch (Exception e) {
      logger.error(e.getMessage());
      throw new Exception(e.getMessage());
    } finally {
      bos.close();
      // Marking for GC
      bos = null;
    }
  }

  /**
   * A method used for downloading a resource from external sources.
   * 
   * @param downloadURL
   * @param fileDownloadLocation
   * @throws Exception 
   */
  public static void download(String downloadURL, String fileDownloadLocation) throws Exception {

    URL link = null;
    InputStream in = null;
    FileOutputStream fos = null;

    try {

      link = new URL(downloadURL);
      in = new BufferedInputStream(link.openStream());
      fos = new FileOutputStream(fileDownloadLocation);
      byte[] buf = new byte[1024 * 1024]; // 1mb blocks
      int n = 0;
      long size = 0;
      while (-1 != (n = in.read(buf))) {
        size += n;
        logger.debug("\r" + size + " ");
        fos.write(buf, 0, n);
      }
      fos.close();
      in.close();
    } catch (Exception e) {
      logger.error(e.getMessage());
      throw new Exception(e.getMessage());
    }
  }

  /**
   * A method used for extracting files from a zip archive.
   * 
   * @param filename
   * @param filePath
   * @throws IOException
   */
  public static void extract(String filename, String filePath) throws IOException {
    logger.debug(" Attempting to unzip the downloaded release ...");
    try {
      logger.debug("File to be copied: " + filename);
      logger.debug("Location: " + filePath);
      ZipFile zip = new ZipFile(filename);
      zip.extractAll(filePath);
    } catch (ZipException ex) {
      logger.error(ex.getMessage());
      throw new IOException(ex);
    }
  }

  /**
   * A method used for fetching latest commit from a remote repository.
   * 
   * @param repositoryURL
   * @return
   * @throws IOException
   */
  public static String getLatestCommitID(String repositoryURL) throws IOException {
    logger.debug(" Getting the latest commit ID from: " + repositoryURL);
    return new BufferedReader(new InputStreamReader(
        Runtime.getRuntime().exec("git ls-remote " + repositoryURL + " HEAD").getInputStream())).readLine()
        .split("HEAD")[0].trim();
  }

  /**
   * A method used for getting the repository path.
   * 
   * @return
   */
  public static String getLocalRepoPath() {
    Util.GIT_REPOSITORY_PATH = Util.DOWNLOAD_DIR + "git-repository";
    return Util.GIT_REPOSITORY_PATH;
  }

  /**
   * A method used for getting the commit information.
   * 
   * @return String
   * @throws Exception 
   */
  public static String getCommitInformation() throws Exception {
    logger.debug(" Getting the latest commit Information from local repository");
    File directory = new File(Util.getLocalRepoPath());
    directory.setExecutable(true);
    BufferedReader reader;
    String line = "";
    String returnString = "";

    try {
      reader = new BufferedReader(new InputStreamReader(Runtime.getRuntime()
          .exec("git show --no-patch " + Util.COMMIT_ID, new String[] {}, directory).getInputStream()));

      while ((line = reader.readLine()) != null) {
        returnString += line.replaceAll("<", " ").replaceAll(">", " ").replaceAll(",", "").trim() + "<br/>";
      }

      return returnString;
    } catch (IOException e) {
      logger.error(e.getMessage());
      throw new Exception(e.getMessage());
    } finally {
      // Marking for GC
      directory = null;
      reader = null;
      line = null;
      returnString = null;
    }

  }

  /**
   * A method used for reading the property file and injecting data into the
   * data variables.
   * @throws Exception 
   */
  public static void getPropertyValues() throws Exception {

    // THIS METHOD SHOULD BE CALLED BEFORE ANYOTHER METHOD

    Properties prop = new Properties();
    InputStream input = null;

    try {

      input = new FileInputStream("config.properties");
      prop.load(input);

      // get the property value and print it out
      Util.ZOOKEEPER_DOWNLOAD_URL = prop.getProperty("SolrNightlyBenchmarks.zookeeperDownloadURL");
      logger.debug("Getting Property Value for zookeeperDownloadURL: " + Util.ZOOKEEPER_DOWNLOAD_URL);
      Util.ZOOKEEPER_RELEASE = prop.getProperty("SolrNightlyBenchmarks.zookeeperDownloadVersion");
      logger.debug("Getting Property Value for zookeeperDownloadVersion: " + Util.ZOOKEEPER_RELEASE);
      Util.ZOOKEEPER_IP = prop.getProperty("SolrNightlyBenchmarks.zookeeperHostIp");
      logger.debug("Getting Property Value for zookeeperHostIp: " + Util.ZOOKEEPER_IP);
      Util.ZOOKEEPER_PORT = prop.getProperty("SolrNightlyBenchmarks.zookeeperHostPort");
      logger.debug("Getting Property Value for zookeeperHostPort: " + Util.ZOOKEEPER_PORT);
      Util.LUCENE_SOLR_REPOSITORY_URL = prop.getProperty("SolrNightlyBenchmarks.luceneSolrRepositoryURL");
      logger.debug("Getting Property Value for luceneSolrRepositoryURL: " + Util.LUCENE_SOLR_REPOSITORY_URL);
      Util.METRIC_ESTIMATION_PERIOD = prop.getProperty("SolrNightlyBenchmarks.metricEstimationPeriod");
      logger.debug("Getting Property Value for metricEstimationPeriod: " + Util.METRIC_ESTIMATION_PERIOD);
      Util.QUERY_THREAD_COUNT_FIRST = prop.getProperty("SolrNightlyBenchmarks.queryThreadCountFirst");
      logger.debug("Getting Property Value for queryThreadCountFirst: " + Util.QUERY_THREAD_COUNT_FIRST);
      Util.QUERY_THREAD_COUNT_SECOND = prop.getProperty("SolrNightlyBenchmarks.queryThreadCountSecond");
      logger.debug("Getting Property Value for queryThreadCountSecond: " + Util.QUERY_THREAD_COUNT_SECOND);
      Util.QUERY_THREAD_COUNT_THIRD = prop.getProperty("SolrNightlyBenchmarks.queryThreadCountThird");
      logger.debug("Getting Property Value for queryThreadCountThird: " + Util.QUERY_THREAD_COUNT_THIRD);
      Util.QUERY_THREAD_COUNT_FOURTH = prop.getProperty("SolrNightlyBenchmarks.queryThreadCountFourth");
      logger.debug("Getting Property Value for queryThreadCountFourth: " + Util.QUERY_THREAD_COUNT_FOURTH);
      Util.TEST_DATA_DIRECTORY = prop.getProperty("SolrNightlyBenchmarks.testDataDirectory");
      logger.debug("Getting Property Value for testDataDirectory: " + Util.TEST_DATA_DIRECTORY);
      Util.ONEM_TEST_DATA = prop.getProperty("SolrNightlyBenchmarks.1MTestData");
      logger.debug("Getting Property Value for 1MTestData: " + Util.ONEM_TEST_DATA);
      Util.NUMERIC_QUERY_TERM_DATA = prop.getProperty("SolrNightlyBenchmarks.staticNumericQueryTermsData");
      logger.debug("Getting Property Value for staticNumericQueryTermsData: " + Util.NUMERIC_QUERY_TERM_DATA);
      Util.NUMERIC_QUERY_PAIR_DATA = prop.getProperty("SolrNightlyBenchmarks.staticNumericQueryPairsData");
      logger.debug("Getting Property Value for staticNumericQueryPairsData: " + Util.NUMERIC_QUERY_PAIR_DATA);
      Util.TEST_WITH_NUMBER_OF_DOCUMENTS = Long
          .parseLong(prop.getProperty("SolrNightlyBenchmarks.testWithNumberOfDocuments"));
      logger.debug(
          "Getting Property Value for testWithNumberOfDocuments: " + Util.TEST_WITH_NUMBER_OF_DOCUMENTS);
      Util.NUMERIC_SORTED_QUERY_PAIR_DATA = prop
          .getProperty("SolrNightlyBenchmarks.staticNumericSortedQueryPairsData");
      logger.debug("Getting Property Value for staticNumericSortedQueryPairsData: "
          + Util.NUMERIC_SORTED_QUERY_PAIR_DATA);
      Util.USE_COLORED_TEXT_ON_CONSOLE = new Boolean(
          prop.getProperty("SolrNightlyBenchmarks.useColoredTextOnConsole"));
      logger.debug("Getting Property Value for useColoredTextOnConsole: " + Util.USE_COLORED_TEXT_ON_CONSOLE);
      Util.NUMERIC_QUERY_AND_OR_DATA = prop.getProperty("SolrNightlyBenchmarks.staticNumericQueryAndOrTermsData");
      logger.debug(
          "Getting Property Value for staticNumericQueryAndOrTermsData: " + Util.NUMERIC_QUERY_AND_OR_DATA);
      Util.TEXT_TERM_DATA = prop.getProperty("SolrNightlyBenchmarks.staticTextTermQueryData");
      logger.debug("Getting Property Value for staticTextTermQueryData: " + Util.TEXT_TERM_DATA);
      Util.TEXT_PHRASE_DATA = prop.getProperty("SolrNightlyBenchmarks.staticTextPhraseQueryData");
      logger.debug("Getting Property Value for staticTextPhraseQueryData: " + Util.TEXT_PHRASE_DATA);
      Util.HIGHLIGHT_TERM_DATA = prop.getProperty("SolrNightlyBenchmarks.highlightTermsData");
      logger.debug("Getting Property Value for highlightTermsData: " + Util.HIGHLIGHT_TERM_DATA);
      Util.TEST_DATA_STORE_LOCATION = prop.getProperty("SolrNightlyBenchmarks.testDataStoreURL");
      logger.debug("Getting Property Value for testDataStoreURL: " + Util.TEST_DATA_STORE_LOCATION);
      Util.RANGE_FACET_DATA = prop.getProperty("SolrNightlyBenchmarks.rangeFacetTestData");
      logger.debug("Getting Property Value for rangeFacetTestData: " + Util.RANGE_FACET_DATA);
      Util.TEST_DATA_ARCHIVE_LOCATION = prop.getProperty("SolrNightlyBenchmarks.testDataArchiveLocation");
      logger.debug("Getting Property Value for testDataArchiveLocation: " + Util.TEST_DATA_ARCHIVE_LOCATION);
      Util.NUMBER_OF_QUERIES_TO_RUN = Long.parseLong(prop.getProperty("SolrNightlyBenchmarks.numberOfQueriesToRun"));
      logger.debug("Getting Property Value for numberOfQueriesToRun: " + Util.NUMBER_OF_QUERIES_TO_RUN);
      Util.NUMBER_OF_QUERIES_TO_RUN_FOR_FACETING = Long.parseLong(prop.getProperty("SolrNightlyBenchmarks.numberOfQueriesToRunForFaceting"));
      logger.debug("Getting Property Value for numberOfQueriesToRunForFaceting: " + Util.NUMBER_OF_QUERIES_TO_RUN_FOR_FACETING);

    } catch (IOException ex) {
      logger.error(ex.getMessage());
      throw new Exception(ex.getMessage());
    } finally {
      if (input != null) {
        try {
          input.close();
        } catch (IOException e) {
          logger.error(e.getMessage());
          throw new Exception(e.getMessage());
        }
      }
      // Marking for GC
      input = null;
      prop = null;
    }

  }

  /**
   * A method used for sending requests to web resources.
   * 
   * @param url
   * @param type
   * @return
   * @throws Exception 
   */
  public static String getResponse(String url, String type) throws Exception {

    Client client;
    ClientResponse response;

    try {
      client = Client.create();
      WebResource webResource = client.resource(url);
      response = webResource.accept(type).get(ClientResponse.class);

      if (response.getStatus() != 200) {
        logger.error("Failed : HTTP error code : " + response.getStatus());
        throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
      }

      return response.getEntity(String.class);
    } catch (Exception e) {
      logger.error(e.getMessage());
      throw new Exception(e.getMessage());
    } finally {
      // Marking for GC
      client = null;
      response = null;
    }
  }

  /**
   * A method used for generating random sentences for tests.
   * 
   * @param r
   * @param words
   * @return String
   */
  public static String getSentence(Random r, int words) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < words; i++) {
      sb.append(TestUtil.randomSimpleString(r, 4 + r.nextInt(10)) + " ");
    }
    return sb.toString().trim();
  }

  /**
   * A method used for locating and killing unused processes.
   * 
   * @param lookFor
   * @throws IOException 
   */
  public static void killProcesses(String lookFor) throws IOException {

    logger.debug(" Searching and killing " + lookFor + " process(es) ...");

    BufferedReader reader;
    String line = "";

    try {
      String[] cmd = { "/bin/sh", "-c", "ps -ef | grep " + lookFor + " | awk '{print $2}'" };
      reader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(cmd).getInputStream()));

      while ((line = reader.readLine()) != null) {

        line = line.trim();
        logger.debug(" Found " + lookFor + " Running with PID " + line + " Killing now ..");
        Runtime.getRuntime().exec("kill -9 " + line);
      }

      reader.close();

    } catch (IOException e) {
      logger.error(e.getMessage());
      throw new IOException(e.getMessage());
    } finally {
      // Marking for GC
      reader = null;
      line = null;
    }

  }

  /**
   * A utility method used for getting the head name.
   * @param repo
   * @return string
   * @throws Exception 
   */
  public static String getHeadName(Repository repo) throws Exception {
    String result = null;
    try {
      ObjectId id = repo.resolve(Constants.HEAD);
      result = id.getName();
    } catch (IOException e) {
      logger.error(e.getMessage());
      throw new Exception(e.getMessage());
    }
    return result;
  }

  public static String getDateString(int epoch) {
    Date date = new Date(1000L * epoch);
    SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
    String dateStr = format.format(date);
    return dateStr;
  }

  public static void outputMetrics(String filename, Map<String, String> timings) throws Exception {
    File outputFile = new File(filename);

    String header[] = new String[0];
    List<Map<String, String>> lines = new ArrayList<>();

    if (outputFile.exists()) {
      CSVReader reader = new CSVReader(new FileReader(outputFile));

      String tmp[] = reader.readNext();
      header = new String[tmp.length];
      for (int i=0; i<header.length; i++) {
        header[i] = tmp[i].trim();
      }

      String line[];
      while((line=reader.readNext()) != null) {
        if (line.length != header.length) {
          continue;
        }
        Map<String, String> mappedLine = new LinkedHashMap<>();

        for (int i=0; i<header.length; i++) {
          mappedLine.put(header[i], line[i]);
        }
        lines.add(mappedLine);
      }
      reader.close();
    }

    LinkedHashSet<String> newHeaders = new LinkedHashSet<>(Arrays.asList(header));
    newHeaders.addAll(timings.keySet());

    lines.add(timings);
    FileWriter out = new FileWriter(filename);
    out.write(Arrays.toString(newHeaders.toArray()).replaceAll("\\[", "").replaceAll("\\]", "") + "\n");
    for (Map<String, String> oldLine: lines) {
      for (int i=0; i<newHeaders.size(); i++) {
        String col = oldLine.get(newHeaders.toArray()[i]);
        out.write(col == null? " ": col);
        if (i==newHeaders.size()-1) {
          out.write("\n");
        } else {
          out.write(",");
        }
      }
    }
    out.close();                            
  }

  public static class ProcessStreamReader extends Thread {

    public final static Logger logger = Logger.getLogger(ProcessStreamReader.class);

    InputStream is;
    String type;

    /**
     * Constructor.
     * 
     * @param is
     * @param type
     */
    ProcessStreamReader(InputStream is, String type) {
      this.is = is;
      this.type = type;
    }

    /**
     * A method invoked by process execution thread.
     */
    public void run() {
      try {
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        String line = null;
        while ((line = br.readLine()) != null) {
          logger.debug(">> " + line);
        }

      } catch (IOException ioe) {
        logger.error(ioe.getMessage());
        throw new RuntimeException(ioe.getMessage());
      }
    }
  }
}
