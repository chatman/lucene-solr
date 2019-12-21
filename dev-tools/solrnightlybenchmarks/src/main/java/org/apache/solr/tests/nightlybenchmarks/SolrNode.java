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

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

enum SolrNodeAction {
  NODE_START, NODE_STOP
}

/**
 * This class provides a blueprint for Solr Node.
 * @author Vivek Narang
 *
 */
public class SolrNode {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final String URL_BASE = "http://archive.apache.org/dist/lucene/solr/";

  public boolean isRunningInCloudMode;
  public String solrDirName;
  public String collectionName;
  public String baseDirectory;
  public String port;
  private String nodeDirectory;
  private final String commitId;
  public int commitTime;
  private final Zookeeper zookeeper;
  private String gitDirectoryPath = Util.DOWNLOAD_DIR + "git-repository-";

  /**
   * Constructor. 
   * @param commitId
   * @param zooKeeperIp
   * @param zooKeeperPort
   * @param isRunningInCloudMode
   * @throws Exception 
   */
  public SolrNode(String commitId, Zookeeper zookeeper, boolean isRunningInCloudMode)
      throws Exception {
    super();
    this.commitId = commitId;
    this.zookeeper = zookeeper;
    this.isRunningInCloudMode = isRunningInCloudMode;
    //this.gitDirectoryPath = Util.DOWNLOAD_DIR + "git-repository-" + commitId;
    this.gitDirectoryPath = Util.DOWNLOAD_DIR + "git-repository";
    Util.GIT_REPOSITORY_PATH = this.gitDirectoryPath;
    this.install();
  }

  /**
   * A method used for initializing the solr node.
   * @throws Exception 
   * @throws InterruptedException 
   */
  private void install() throws Exception {

    log.debug("Installing Solr Node ...");

    this.port = String.valueOf(Util.getFreePort());

    this.baseDirectory = Util.SOLR_DIR + UUID.randomUUID().toString() + File.separator;
    this.nodeDirectory = this.baseDirectory;

    try {

      log.debug("Checking if SOLR node directory exists ...");

      File node = new File(nodeDirectory);

      if (!node.exists()) {

        log.debug("Node directory does not exist, creating it ...");
        node.mkdir();
      } 

    } catch (Exception e) {
      log.error(e.getMessage());
      throw new Exception(e.getMessage());
    }

    this.checkoutCommitAndBuild();
    Util.extract(Util.DOWNLOAD_DIR + "solr-" + commitId + ".zip", nodeDirectory);

    this.nodeDirectory = new File(this.nodeDirectory).listFiles()[0] + File.separator + "bin" + File.separator;
  }

  /**
   * A method used for checking out the solr code based on a commit and build the package for testing.
   * @throws Exception 
   */
  void checkoutCommitAndBuild() throws Exception {

    log.info("Checking out Solr: " + commitId + " ...");

    File gitDirectory = new File(gitDirectoryPath);
    Git repository;

    if (gitDirectory.exists()) {
      repository = Git.open(gitDirectory);

      if (!Util.getHeadName(repository.getRepository()).equals(commitId)) {

        repository.checkout().setName("master").call();

        PullCommand pullCmd = repository.pull();
        try {
          pullCmd.call();
        } catch (GitAPIException e) {
          log.error(e.getMessage());
          throw new Exception(e.getMessage());
        }

        repository.checkout().setName(commitId).call();
      }
    } else {
      repository = Git.cloneRepository().setURI(Util.LUCENE_SOLR_REPOSITORY_URL).setDirectory(gitDirectory)
          .call();
      repository.checkout().setName(commitId).call();
    }

    for (RevCommit revCommit: repository.log().call()) {
      if (revCommit.getId().name().equals(commitId)) {
        this.commitTime = revCommit.getCommitTime();
        break;
      }
    }

    String packageFilename = gitDirectoryPath + "/solr/package/";
    Util.SOLR_PACKAGE_DIR = packageFilename;
    Util.SOLR_PACKAGE_DIR_LOCATION = gitDirectoryPath;
    String tarballLocation = Util.DOWNLOAD_DIR + "solr-" + commitId + ".zip";

    if (!new File(tarballLocation).exists()) {
      log.debug("There were new changes, need to rebuild ...");
      Util.execute("ant ivy-bootstrap", gitDirectoryPath);
      // Util.execute("ant compile", gitDirectoryPath);
      Util.execute("ant package", gitDirectoryPath + File.separator + "solr");

      if (new File(packageFilename).exists()) {

        File subDirPath = new File(gitDirectoryPath + "/solr/package/");
        String fileName = "";
        if (subDirPath.exists()) {
          File[] files = subDirPath.listFiles();
          for (int i = 0; i < files.length; i++) {
            if (FilenameUtils.isExtension(files[i].getName(), "zip")) {
              fileName = files[i].getName();
              break;
            }
          }
        }
        this.solrDirName = fileName;
        packageFilename += fileName;

        log.debug("Trying to copy: " + packageFilename + " to " + tarballLocation);
        Files.copy(Paths.get(packageFilename), Paths.get(tarballLocation));
        log.debug("File copied!");

      } else {
        log.error("Couldn't build the package");
        throw new IOException("Couldn't build the package"); 
      }
    } 

    log.debug("Do we have packageFilename? " + (new File(tarballLocation).exists() ? "yes" : "no") + " ...");
  }

  /**
   * A method used to do action (start, stop ... etc.) a solr node. 
   * @param action
   * @return
   * @throws Exception 
   */
  public int doAction(SolrNodeAction action) throws Exception {

    long start = 0;
    long end = 0;
    int returnValue = 0;

    start = System.currentTimeMillis();
    new File(nodeDirectory + "solr").setExecutable(true);
    if (action == SolrNodeAction.NODE_START) {

      if (isRunningInCloudMode) {
        returnValue = Util.execute(nodeDirectory + "solr start -force -Dhost=localhost " + "-p " + port + " -m 5g " + " -z "
            + zookeeper.getZookeeperIp() + ":" + zookeeper.getZookeeperPort(), nodeDirectory);
      } else {
        returnValue = Util.execute(nodeDirectory + "solr start -force " + "-p " + port + " -m 5g ",
            nodeDirectory);
      }

    } else if (action == SolrNodeAction.NODE_STOP) {

      if (isRunningInCloudMode) {
        returnValue = Util.execute(
            nodeDirectory + "solr stop -p " + port + " -z " + zookeeper.getZookeeperIp() + ":" + zookeeper.getZookeeperPort() + " -force",
            nodeDirectory);
      } else {
        returnValue = Util.execute(nodeDirectory + "solr stop -p " + port + " -force", nodeDirectory);
      }

    }
    end = System.currentTimeMillis();

    log.info("Time taken for the node " + action + " activity is: " + (end - start) + " millisecond(s)");
    return returnValue;
  }

  /**
   * A method used for creating collection on the solr cloud. 
   * @param collectionName
   * @param configName
   * @param shards
   * @param replicationFactor
   * @return
   * @throws Exception 
   */
  @SuppressWarnings("deprecation")
  public Map<String, String> createCollection(String collectionName, String configName, int shards,
      int replicas) throws Exception {

    this.collectionName = collectionName;
    log.info("Creating collection: " + collectionName + " ... ");

    long start;
    long end;
    int returnVal;

    if (configName != null) {
      start = System.currentTimeMillis();
      returnVal = Util.execute("./solr create_collection -collection " + collectionName + " -shards " + shards
          + " -n " + configName + " -replicationFactor " + replicas + " -force", nodeDirectory);
      end = System.currentTimeMillis();
    } else {
      start = System.currentTimeMillis();
      returnVal = Util.execute("./solr create_collection -collection " + collectionName + " -shards " + shards
          + " -replicationFactor " + replicas + " -force", nodeDirectory);
      end = System.currentTimeMillis();
    }

    log.info("Time for creating the collection is: " + (end - start) + " millisecond(s)");

    Thread.sleep(5000);

    Map<String, String> returnMap = new HashMap<String, String>();
    Date dNow = new Date();
    SimpleDateFormat ft = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    returnMap.put("ProcessExitValue", "" + returnVal);
    returnMap.put("TimeStamp", "" + ft.format(dNow));
    returnMap.put("CreateCollectionTime", "" + ((double) (end - start) / 1000d));
    returnMap.put("CommitID", this.commitId);

    return returnMap;
  }

  /**
   * A method for deleting a collection on a solr cloud or a node. 
   * @param collectionName
   * @return
   * @throws Exception 
   */
  public int deleteCollection(String collectionName) throws Exception {

    this.collectionName = collectionName;
    log.info("Deleting collection: "+collectionName+" ... ");
    return Util.execute("./solr delete -c " + collectionName + " -deleteConfig true", nodeDirectory);

  }

  /**
   * A method to get the node directory for the solr node. 
   * @return
   */
  public String getNodeDirectory() {
    return nodeDirectory;
  }

  /**
   * A method used for getting the URL for the solr node for communication. 
   * @return
   */
  public String getBaseUrl() {
    return "http://localhost:" + port + "/solr/";
  }

  /**
   * A method used for getting the URL (containing the collection reference) for the solr node.  
   * @return
   */
  public String getBaseUrlC() {
    return "http://localhost:" + port + "/solr/" + this.collectionName;
  }

  /**
   * A method used for cleaning up the files for the solr node. 
   * @throws Exception 
   */
  public void cleanup() throws Exception {
    Util.execute("rm -r -f " + baseDirectory, baseDirectory);
  }
}