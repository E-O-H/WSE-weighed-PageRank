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
import org.jsoup.safety.Whitelist;
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
   * command-line arguments for the entry point
   */
  @Option(name = "-docs", aliases = "-d", required = true, 
      usage = "Path to the directory containing pages.")
  private Path htmlDir;
  
  @Option(name = "-f", required = true, 
      usage = "The probability of following links in the PageRank model.")
  private double fVal;
  
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
    
    public String name;                 // File name of page
    public double quality;              // Inherent quality of page (phi)
    public double base;                 // Normalized quality of page (phi')
    public List<String> outLinks;       // All out-links from the page
    public List<Double> outLinkScores;  // All out-link scores
    
    Page(String name, double quality) {
      this.quality = quality;
      outLinks = new ArrayList<String>();
      outLinkScores = new ArrayList<Double>();
    }
    
  }
  
  /*
   * Constants
   */
  private static final double LINK_BASE_SCORE = 1;
  private static final double LINK_BONUS_SCORE = 1;
  
  /*
   * internal storage
   */
  private double epsilon;
  private List<Page> pages = new ArrayList<Page>();
  
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
  /*
N = number of pages in the collection;
epsilon = 0.01/N;
for (each page P in the collection)     % Calculate base values
   P.base = log2 WordCount(P);
sum = ¦²P P.base;
for (each page P in the collection)     % Initialize score
   P.score = P.base = P.base/sum;
      
Weight = NxN array of 0;
for (each page P in the collection)     % Calculate link weighte
   if (P has no outlinks)
        for (each Q in the collection) Weight[Q,P] = Q.score;
     else {
        for (each outlink P -> Q) 
            Weight[Q,P] = CalculateWeight(P,Q);
        sum = ¦²Q Weight[Q,P]
        for (each outlink P -> Q) 
            Weight[Q,P] = Weight[Q,P]/sum;
   endif
endfor*/
  
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
    
    
    return 0;
  }
  
  /**
   * Start the PageRank algorithm
   */
  private void start() {
    prepareValues();
    
  }

/*
repeat {                                % Main loop
    changed = false;
    for (each page P in the collection) {
        P.newscore = (1-F) * P.base + F * ¦²Q Q.score * Weight[P,Q];
        if (abs(P.newscore - P.score) > epsilon) changed = true;
       }
    for (each page P in the collection)     % Initialize score
       P.score = P.newscore;
} while changed;
   */
  
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
