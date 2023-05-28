package App;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Parser {
    private Document doc;

    public Parser(String url) throws IOException {
        getPage(url);
    }

    private void getPage(String url) throws IOException {
        doc = Jsoup.connect(url).get();
    }
    
   // функция разделения текста на предложения
    private String[] splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        StringBuilder currentSentence = new StringBuilder();
        int openParenthesesCount = 0;

        for (int i = 0; i < text.length(); i++) {
            char currentChar = text.charAt(i);
            currentSentence.append(currentChar);

            if (currentChar == '(') {
                openParenthesesCount++;
            } else if (currentChar == ')') {
                openParenthesesCount--;
            } else if (currentChar == '.' && i < text.length() - 1 && text.charAt(i + 1) == ' ') {
                if (openParenthesesCount == 0 && i < text.length() - 2 && Character.isUpperCase(text.charAt(i + 2))) {
                    sentences.add(currentSentence.toString());
                    currentSentence = new StringBuilder();
                    i++; // Skip the space after the period
                }
            }
        }

        if (currentSentence.length() > 0) {
            sentences.add(currentSentence.toString());
        }

        return sentences.toArray(new String[0]);
    }
    
    // функция для подсчета ключевых слов
    private int countKeywords(String sentence, String[] keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (sentence.contains(keyword)) {
                count++;
            }
        }
        return count;
    }

    // функция для получения индексов максимальных совпадений ключевых слов в предложении
    private int[] getSortedMaxMatchIndices(final int[] max_match) {
    	Integer[] indices = new Integer[max_match.length];
    	for (int i = 0; i < max_match.length; i++) {
    		indices[i] = i;
    	}

    	Arrays.sort(indices, new Comparator<Integer>() {
        @Override
        public int compare(Integer a, Integer b) {
            return Integer.compare(max_match[b], max_match[a]);
        }
    	});

    	int[] result = new int[max_match.length];
    	for (int i = 0; i < max_match.length; i++) {
    		result[i] = indices[i];
    	}

    	return result;
    }
    
    private String[] getVariants(String[] keywords) {
        String[] variants = new String[keywords.length * 3];

        for (int i = 0; i < keywords.length; i++) {
            String keyword = keywords[i];
            variants[i * 3] = keyword.toLowerCase();                  // Нижний регистр
            variants[i * 3 + 1] = keyword.substring(0, 1).toUpperCase() + keyword.substring(1);  // Первая буква заглавная
            variants[i * 3 + 2] = keyword.toUpperCase();              // Верхний регистр
        }
        
        return variants;
    }
    
    // функция выдачи предложений из сайта для preview по ключевым словам
    public String getPagePreview(String[] keywords, int quantity_of_sentences) {
    	String _text = doc.text();
    	String[] _lines = splitIntoSentences(_text);
    	String[] _keywords = getVariants(keywords);

    	int[] _matches = new int [_lines.length];
    	for (int i = 0; i < _lines.length; i++) {
    		_matches[i] = countKeywords(_lines[i], _keywords);
    	}
    	
    	int[] max_match = getSortedMaxMatchIndices(_matches);
    	
    	String result = "";
    	for (int i = 0; i < quantity_of_sentences; i++) {
    		result += _lines[max_match[i]] + ".\\s";
    	}
    	return result;
    }
    
    public String get(String request) {
    	request = request.replace(".", "").replace(" ", "");
    	String[] keywords = request.split(",");

        String[] results = getVariants(keywords);
        
        
        return getPagePreview(results, 3);
    }
}
