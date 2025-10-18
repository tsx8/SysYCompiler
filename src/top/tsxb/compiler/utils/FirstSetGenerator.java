package top.tsxb.compiler.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A utility to compute the FIRST sets for the SysY grammar. This tool is implemented with pure Java
 * 17 and has zero external dependencies. It defaults to using the 'doc/SysY.g4' file and now uses a
 * robust parser to correctly handle EBNF grammar rules with nested alternatives.
 */
public final class FirstSetGenerator {

  private static final Path GRAMMAR_FILE_PATH = Path.of("doc", "SysY.g4");
  private static final String EPSILON = "ε";

  private record Grammar(Map<String, List<List<String>>> rules, Set<String> nonTerminals) {}

  /**
   * The entry point of application.
   *
   * @param args the input arguments
   */
  public static void main(String[] args) {
    if (!Files.isReadable(GRAMMAR_FILE_PATH)) {
      System.err.println(
          "Error: Cannot read default grammar file: " + GRAMMAR_FILE_PATH.toAbsolutePath());
      System.exit(1);
    }

    try {
      System.out.println("Parsing grammar from: " + GRAMMAR_FILE_PATH.toAbsolutePath());
      String content = Files.readString(GRAMMAR_FILE_PATH);
      Grammar grammar = parseGrammar(content);
      System.out.println(
          "Grammar parsed successfully. Found " + grammar.rules().size() + " non-terminals.");

      System.out.println("\nPass 1: Computing nullable non-terminals...");
      Set<String> nullableNonTerminals = computeNullableNonTerminals(grammar);
      System.out.println(
          "Nullable non-terminals: " + nullableNonTerminals.stream().sorted().toList());

      System.out.println("\nPass 2: Computing FIRST sets...");
      Map<String, Set<String>> firstSets = computeFirstSets(grammar, nullableNonTerminals);
      System.out.println("Computation complete.");

      System.out.println("\n--- FIRST Sets ---");
      printFirstSets(firstSets);

    } catch (IOException e) {
      System.err.println("Error reading grammar file: " + e.getMessage());
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  /** A more robust grammar parser that correctly handles nested structures. */
  private static Grammar parseGrammar(String content) {
    final Pattern rulePattern = Pattern.compile("(\\b[a-z]\\w*)\\s*:\\s*(.*?);", Pattern.DOTALL);
    final Pattern symbolPattern =
        Pattern.compile("\\b[A-Za-z_]\\w*[*?+]?"); // Extracts grammar symbols

    Map<String, List<List<String>>> rules = new HashMap<>();
    Matcher ruleMatcher = rulePattern.matcher(content);

    while (ruleMatcher.find()) {
      String nonTerminal = ruleMatcher.group(1);
      String body = ruleMatcher.group(2);

      List<String> alternatives = splitByTopLevelOr(body);

      List<List<String>> productions = new ArrayList<>();
      for (String alt : alternatives) {
        List<String> symbols = new ArrayList<>();
        Matcher symbolMatcher = symbolPattern.matcher(alt);
        while (symbolMatcher.find()) {
          symbols.add(symbolMatcher.group());
        }
        if (!symbols.isEmpty()) {
          productions.add(symbols);
        }
      }
      rules.put(nonTerminal, productions);
    }

    return new Grammar(rules, new HashSet<>(rules.keySet()));
  }

  /**
   * Splits a grammar rule body by the '|' character, but only at the top level (not inside
   * parentheses).
   */
  private static List<String> splitByTopLevelOr(String body) {
    List<String> alternatives = new ArrayList<>();
    int parenCount = 0;
    int lastSplit = 0;
    for (int i = 0; i < body.length(); i++) {
      char c = body.charAt(i);
      if (c == '(') {
        parenCount++;
      } else if (c == ')') {
        parenCount--;
      } else if (c == '|' && parenCount == 0) {
        alternatives.add(body.substring(lastSplit, i).trim());
        lastSplit = i + 1;
      }
    }
    alternatives.add(body.substring(lastSplit).trim());
    return alternatives;
  }

  private static Set<String> computeNullableNonTerminals(Grammar grammar) {
    Set<String> nullable = new HashSet<>();
    boolean changed = true;
    while (changed) {
      changed = false;
      for (Map.Entry<String, List<List<String>>> rule : grammar.rules().entrySet()) {
        String nonTerminal = rule.getKey();
        if (nullable.contains(nonTerminal)) {
          continue;
        }
        for (List<String> production : rule.getValue()) {
          if (production.isEmpty()
              || production.stream()
                  .allMatch(s -> isSymbolNullable(s, nullable, grammar.nonTerminals()))) {
            if (nullable.add(nonTerminal)) {
              changed = true;
            }
            break;
          }
        }
      }
    }
    return nullable;
  }

  private static boolean isSymbolNullable(
      String symbol, Set<String> nullableNonTerminals, Set<String> allNonTerminals) {
    if (symbol.endsWith("*") || symbol.endsWith("?")) {
      return true;
    }
    String baseSymbol = symbol.replaceAll("[*?+]$", "");
    if (!allNonTerminals.contains(baseSymbol)) { // It's a terminal
      return false;
    }
    return nullableNonTerminals.contains(baseSymbol);
  }

  private static Map<String, Set<String>> computeFirstSets(
      Grammar grammar, Set<String> nullableNonTerminals) {
    Map<String, Set<String>> firstSets = new HashMap<>();
    grammar.nonTerminals().forEach(nt -> firstSets.put(nt, new HashSet<>()));

    boolean changed = true;
    while (changed) {
      changed = false;

      for (String nonTerminal : grammar.nonTerminals()) {
        for (List<String> production : grammar.rules().get(nonTerminal)) {
          boolean allPreviousNullable = true;
          for (String symbol : production) {
            String baseSymbol = symbol.replaceAll("[*?+]$", "");

            Set<String> firstOfSymbol;
            if (isTerminal(baseSymbol, grammar.nonTerminals())) {
              firstOfSymbol = Collections.singleton(baseSymbol);
            } else {
              firstOfSymbol = firstSets.get(baseSymbol);
            }

            int oldSize = firstSets.get(nonTerminal).size();
            firstOfSymbol.stream()
                .filter(s -> !s.equals(EPSILON))
                .forEach(s -> firstSets.get(nonTerminal).add(s));

            if (firstSets.get(nonTerminal).size() > oldSize) {
              changed = true;
            }

            if (!isSymbolNullable(symbol, nullableNonTerminals, grammar.nonTerminals())) {
              allPreviousNullable = false;
              break;
            }
          }

          if (allPreviousNullable) {
            if (firstSets.get(nonTerminal).add(EPSILON)) {
              changed = true;
            }
          }
        }
      }
    }
    return firstSets;
  }

  private static boolean isTerminal(String symbol, Set<String> nonTerminals) {
    return !nonTerminals.contains(symbol);
  }

  private static void printFirstSets(Map<String, Set<String>> firstSets) {
    new TreeMap<>(firstSets)
        .forEach(
            (nonTerminal, set) -> {
              String formattedSet = set.stream().sorted().collect(Collectors.joining(", "));
              System.out.printf("%-15s: { %s }%n", nonTerminal, formattedSet);
            });
  }
}
