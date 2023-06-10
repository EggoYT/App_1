package App;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import edu.stanford.nlp.simple.Sentence;

public class Parser {
	public Document doc;
	private static double popularicy = 0.25;
	private static int sofar = 3;
	
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

        boolean skobka = false;
        
        for (int i = 0; i < text.length() - 1; i++) {
        	char currentChar = text.charAt(i);
        	if(currentChar == '(')
        		skobka = true;
        	if(skobka) {
        		if(currentChar == ')')
        			skobka = false;
        		continue;
        	}
        	
            char nextChar = text.charAt(i + 1);
            currentSentence.append(currentChar);

            if (currentChar == '.' && nextChar == ' ') {
            	if(currentSentence.length() > 0)
            		sentences.add(currentSentence.toString());
            	currentSentence = new StringBuilder();
                i++; // Skip the space after the period
            }
        }
        currentSentence.append(text.charAt(text.length() - 1));
        
        sentences.add(currentSentence.toString());

        return sentences.toArray(new String[0]);
    }
    
    private int countWordOccurrences(String sentence, String word) {
        // Создаем регулярное выражение для поиска слова с учетом границ слова
        String regex = "\\b" + Pattern.quote(word) + "\\b";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(sentence);

        int count = 0;
        while (matcher.find()) {
            count++;
        }

        return count;
    }
    
    private String getPagePreview(String[] keywords) {
    	String _text = this.doc.select("p").text();
    	String[] _lines = this.splitIntoSentences(_text);
    	
    	double max_weight = 0;
    	int max_weight_id = 0;
    	Sentence sent;
    	for(int i = 0; i < _lines.length; i++) {
    		try {
    			sent = new Sentence(_lines[i].toLowerCase());
    		} catch(IllegalStateException e) {
    			continue;
    		}
    		
    		double weight = 0;
    		for(int j = 0; j < sent.words().size(); j++) {
    			String currentWord = sent.word(j);
    			
    			for(String kw : keywords)
    				for(int k = 0; k < kw.length() && k < currentWord.length(); k++) {
    					if(kw.charAt(k) != currentWord.charAt(k))
    						break;
    					weight += 1.0 / currentWord.length();
    				}
    		}
    		
    		if(max_weight < weight) {
    			max_weight = weight;
    			max_weight_id = i;
			}
    	}
    	
    	String answ = _lines[max_weight_id];
    	return answ.substring(0, Math.min(200, answ.length() - 2)) + "...";
    }
    
    public String get(String request) {
    	request = request.replace(".", "");
    	String[] keywords = request.split(", ");
        return getPagePreview(keywords);
    }
}
