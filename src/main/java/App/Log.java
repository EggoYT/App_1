package App;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

public class Log { //класс для логирования
	private BufferedWriter logging;
	
	public Log(String path) throws IOException {
		logging = new BufferedWriter(new FileWriter(path, true));
	}
	
	public void close() throws IOException {
		logging.close();
	}
	
	public void append(String str) throws IOException {
		append(str, System.currentTimeMillis());
	}
	
	public void append(String str, long timestamp) throws IOException {
		append(str, timestamp, "[INFO]");
	}
	
	public void append(String str, String type) throws IOException{
		append(str, System.currentTimeMillis(), type);
	}
	
	//записываем сообщение в формате [TYPE] ti:me:st a:mp - str
	public void append(String str, long timestamp, String type) throws IOException {
		logging.write(type + " " + new Date(timestamp) + " - " + str + "\n");
		logging.flush();
	}
	
	public void printException(Exception e) throws IOException {
		e.printStackTrace();
		append("Caused server error: " + e.getMessage(), "[ERROR]");
	}
	
	public void printExceptionViaInfo(Exception e, String userId, String companyId, String request) throws IOException { //функция для вывода ошибок с разной информацией
		e.printStackTrace();
		append("Caused server error: " + e.getMessage()
		+ " - \"" + userId + "\" - \"" + companyId + "\" - \""
		+ request + "\"", "[ERROR]");
	}
}
