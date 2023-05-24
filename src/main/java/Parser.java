package App;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class Parser implements AutoCloseable {
	private HashMap<String, String> dictionary;
	private URL url;
	private Document doc;
	
	private void getPage(String page) throws IOException {
		url = new URL("https://" + page);
        doc = Jsoup.parse(url, 3000);
    }
	
	public Parser(String url) throws IOException {
		getPage(url);
		dictionary = new HashMap<String, String>();
	}

	public void close() {
		url = null;
		doc = null;
	}

    public String fillDictionary(String path, String... removalKeywords) {
        Elements elmnts = doc.select(path);

        next:
        for (int i = 0; i < elmnts.size(); i++) {
            String name = elmnts.get(i).text();
            String href = elmnts.get(i).selectFirst("a").attr("href");

            for(String keyword : removalKeywords) {
            	if(name.contains(keyword))
            		continue next;
            }

            href = href.substring(1, href.length());

            dictionary.put(name, href);
        }
	    return "Статья не найдена";
    }

    private static String getState(int state) {
//        try {
//            Elements states = page.select("table > tbody > tr");
//
//            for (int i = 0; i < states.size(); i++) {
//                String name = states.get(i).text();
//                if (name.contains("Раздел") || name.contains("Глава") || name.contains("§"))
//                    continue;
//
//                if (name.contains(" " + Integer.toString(state) + "."))
//                    return name;
//            }
//        } catch(IOException e){
//            System.out.println(e.getMessage());
//            return "Нет такой странички!";
//        }
        return "Статья не найдена";
    }
}
