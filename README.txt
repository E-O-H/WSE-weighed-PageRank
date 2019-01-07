Usage: 
LIBRARY_PATH="lib"
java -cp ".;bin;${LIBRARY_PATH}/args4j-2.33.jar;${LIBRARY_PATH}/jsoup-1.11.3/jsoup-1.11.3.jar;" WeightedPageRank -docs DIRECTORY_PATH -f F_VALUE [-h] 

DIRECTORY_PATH: Path to the directory containing pages.
F_VALUE: The probability of following links in the PageRank model.

Instructions regarding the extra options:
Use -D (-DEBUG) to print debug information (change of each page's score in each iteration step)
Use -h (-help) to print usage.
