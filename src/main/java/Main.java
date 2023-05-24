package App;

import java.io.IOException;

public class Main {

	public static void main(String[] args) {
		try(Parser parser = new Parser("rulaws.ru")){
			parser.fillDictionary("div.sidebar-content > div.widget > ul > li", "Кодексы РФ", "Популярные материалы");
		} catch(IOException e) {
			System.err.println("Can't use parser caused to:\n" + e.getMessage());
		}
		System.out.println("ended");
	}

}
