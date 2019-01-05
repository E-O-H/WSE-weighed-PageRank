import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * Implementation of the Weighted PageRank algorithm.
 *
 * * <p>Usage:<br>
 * Provide arguments for the entry point 
 * "-docs DIRECTORY_PATH -f F_VALUE" 
 * (options can be abbreviated)</p>
 *
 * @author Chenyang Tang
 *
 */
public class WeightedPageRank {
  
  /*
   * Constants
   */
  private static final double LINK_BASE_SCORE = 1;
  private static final double LINK_BONUS_SCORE = 1;
  private static final double EPSILON_MULTIPLIER = 0.01;
  
  /*
   * command-line arguments for the entry point
   */
  @Option(name = "-docs", aliases = "-d", required = true, 
      usage = "Path to the directory containing pages.")
  private Path htmlDir;
  
  @Option(name = "-f", required = true, 
      usage = "The probability of following links in the PageRank model.")
  private double fVal;
  
  @Option(name = "-DEBUG", aliases = "-D", required = false, 
      usage = "Debug mode flag.")
private boolean DEBUG = false;
  
  @Option(name = "-help", aliases = "-h", required = false, 
          usage = "Print this help text.")
  private boolean printHelp = false;
  
  /**
   * class to store information of a page needed in the PageRank algorithm
   * 
   * @author Chenyang Tang
   *
   */
  private class Page {
    
    String name;                 // File name of page
    double quality;              // Inherent quality of page (phi)
    double base;                 // Normalized quality of page (phi')
    double score;                // The PageRank score
    double newScore;             // PageRank score for next iteration
    List<String> outLinks;       // All out-links from the page
    List<Double> outLinkScores;  // All out-link scores (theta)
    
    /**
     * Constructor
     * 
     * @param name Filename of the page
     * @param quality Inherent quality of the page (phi)
     */
    Page(String name, double quality) {
      this.name = name;
      this.quality = quality;
      outLinks = new ArrayList<String>();
      outLinkScores = new ArrayList<Double>();
    }
    
    /**
     * Return the index of another page in this page's outLinks list
     * 
     * @param name Filename of the other page
     * @return the index; or -1 if the queried page is not in this page's outLinks list
     */
    int outLinkIndex(String name) {
      for (int i = 0; i < outLinks.size(); ++i) {
        if (outLinks.get(i).equals(name)) {
          return i;
        }
      }
      return -1;
    }
    
  }
  
  /*
   * internal storage
   */
  private double epsilon;
  private List<Page> pages = new ArrayList<Page>();
  private List<List<Double>> linkWeights;
  
  /**
   * Page quality function
   * 
   * @param html HTML content of the page
   * @return quality score
   */
  private double pageQuality(String rawHtml) {
    StringTokenizer st = new StringTokenizer(rawHtml);
    return Math.log(st.countTokens()) / Math.log(2);
  }
  
  /**
   * Process a single HTML file
   * 
   * @param path path to the HTML file
   * @return status code. 0 for success.
   */
  private void processHtmlFile(Path path) {
    // read HTML file
    String rawHtml;
    try {
      rawHtml = new String(Files.readAllBytes(path), "UTF-8"); 
    } catch (IOException e) {
      System.err.println("Error opening html file " + path + ", skipping this file.");
      return;
    }
    
    // Score the page
    double quality = pageQuality(rawHtml);
    Page page = new Page(path.getFileName().toString(), quality);
    
    // Score all out-links
    Document doc = Jsoup.parse(rawHtml);
    Elements links = doc.select("A[href]");
    for (int i = 0; i < links.size(); ++i) {
      // for every link
      double score = LINK_BASE_SCORE;
      Element ele = links.get(i);
      while(ele.hasParent()) {
        ele = ele.parent();
        if (ele.tagName().equals("H1")
            || ele.tagName().equals("H2")
            || ele.tagName().equals("H3")
            || ele.tagName().equals("H4")
            || ele.tagName().equals("em")
            || ele.tagName().equals("b")) {
          score += LINK_BONUS_SCORE;
        }
      }
      
      // Check if this is a re-occurring link
      boolean exist = false;
      int j;
      for (j = 0; j < page.outLinks.size(); ++j) {
        if (page.outLinks.get(j).equals(links.get(i).attr("href"))) {
          exist = true;
          break;
        }
      }
      if (exist) {
        page.outLinkScores.set(j, page.outLinkScores.get(j) + score);
      } else {
        page.outLinks.add(links.get(i).attr("href"));
        page.outLinkScores.add(score);
      }
    } // for loop
    
    pages.add(page);
  }
  
  /**
   * Prepare all values for use in the linear equations of PageRank algorithm
   * 
   * @return status code. 0 for success. 
   */
  private int prepareValues() {
    // process every file under the path htmlDir
    try (Stream<Path> paths = Files.walk(htmlDir)) {
        paths.filter(Files::isRegularFile)
             .forEach(this::processHtmlFile);
    } catch (IOException e) {
      System.err.println("Error opening html directory" + htmlDir);
      e.printStackTrace();
      return 1;
    }
    
    if (pages.isEmpty()) {
      System.err.println("No HTML file available. Exising.");
      System.exit(-1);
    }
    epsilon = EPSILON_MULTIPLIER / pages.size();
    
    // Calculate normalized page scores
    double sumPageScores = 0;
    for (Page page : pages) {
      sumPageScores += page.quality;
    }
    for (Page page : pages) {
      page.base = page.quality / sumPageScores;
      page.score = page.base;         // Initial value for the iterate-until-convergence method 
    }
    
    // Initialize matrix to store normalized link scores.
    // linkWeights[i, j] represents the weight of link FROM j TO i
    linkWeights = new ArrayList<List<Double>>();
    for (int i = 0; i < pages.size(); ++i) {
      linkWeights.add(new ArrayList<Double>());
      for (int j = 0; j < pages.size(); ++j) {
        linkWeights.get(i).add(Double.valueOf(0));
      }
    }
    
    // Calculate normalized link scores
    for (int i = 0; i < pages.size(); ++i) {
      if (pages.get(i).outLinks.isEmpty()) {
        for (int j = 0; j < pages.size(); ++j) {
          linkWeights.get(j).set(i, Double.valueOf(pages.get(j).score));
        }
      } else {
        double sumOutLinkScores = 0;
        for (int j = 0; j < pages.get(i).outLinkScores.size(); ++j) {
          sumOutLinkScores += pages.get(i).outLinkScores.get(j);
        }
        
        for (int j = 0; j < pages.size(); ++j) {
          int index = pages.get(i).outLinkIndex(pages.get(j).name);
          if (index != -1) {
            double normalizedOutLinkScore = pages.get(i).outLinkScores.get(index) / sumOutLinkScores;
            linkWeights.get(j).set(i, normalizedOutLinkScore);
          }
        }
      }
    }
    
    return 0;
  }
  
  /**
   * Start the PageRank algorithm
   */
  private void start() {
    prepareValues();
    boolean changed;
    do {
      changed = false;
      for (int i = 0; i < pages.size(); ++i) {
        double firstTerm = (1 - fVal) * pages.get(i).base;
        double secondTerm = 0;
        for (int j = 0; j < pages.size(); ++j) {
          secondTerm += fVal * pages.get(j).score * linkWeights.get(i).get(j);
        }
        pages.get(i).newScore =  firstTerm + secondTerm;
        if (Math.abs(pages.get(i).newScore - pages.get(i).score) > epsilon) {
          if (DEBUG) {
            System.err.println(pages.get(i).name + ": " + pages.get(i).score + " > " + pages.get(i).newScore);
          }
          changed = true;
        }
      }
      
      for (int i = 0; i < pages.size(); ++i) {
        pages.get(i).score = pages.get(i).newScore;
      }
    } while (changed);
    
    sortResults();
    printResults();
  }
  
  void sortResults() {
    pages.sort((a, b) -> {
      if (a.score > b.score) return -1;
      else if (a.score < b.score) return 1;
      else return 0;
    }); 
  }
  
  void printResults() {
    for (Page page : pages) {
      String outputName;
      int extensionPos = page.name.indexOf(".html");
      if (extensionPos != -1) {
        outputName = page.name.substring(0, extensionPos);
      } else {
        outputName = page.name;
      }
      
      System.out.format("%15s%.4f\n", outputName, page.score);
    }
  }
  
  private int parseArgs(String[] args) {
    final CmdLineParser args4jCmdLineParser = new CmdLineParser(this);
    try {
      args4jCmdLineParser.parseArgument(args);
    } catch (final CmdLineException e) {
      System.err.println(e.getMessage());
      System.err.println("Usage:");
      args4jCmdLineParser.printUsage(System.err);
      return 2;
    }
    
    if (printHelp) {
      System.err.println("Usage:");
      args4jCmdLineParser.printUsage(System.err);
      return 1;
    }
    
    return 0;
  }

  /**
   * Entry point.
   * 
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    final WeightedPageRank weightedPageRank = new WeightedPageRank();
    int status;
    status = weightedPageRank.parseArgs(args);
    if (status != 0) System.exit(status);
    weightedPageRank.start();
  }

}
