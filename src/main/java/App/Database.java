package App;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

public class Database {
	public HashMap<String, Integer[]> users_info; //ключ - пользователь, значение - информация о нем: 0 - количество оставшихся запросов, 1 - последняя оценка бота
	public HashMap<String, HashSet<String>> companies_info; //ключ - компания, значение - массив пользователей
	String path;
	
	public Database(String path) throws IOException{ //база данных. умеет пока парсить csv, но можно и sql подрубить при желании
		this.path = path;
		users_info = new HashMap<String, Integer[]>();
		companies_info = new HashMap<String, HashSet<String>>();
		loadDatabase();
	}
	
	public void loadDatabase() throws IOException { //функция подгрузки базы данных
		//п@сх@лка 1. Анекдот: На дне океана рыбка-шутник спросила рыбку-грустинку: "Почему ты такая грустная?", а та ответила: "Потому что вода тут соленая, а я забыла свой хлеб".
		try(BufferedReader reader = new BufferedReader(new FileReader(path));) {
			//чисто парсинг csv файла, где все разделено <;>...
			while(reader.ready()) {
				String[] line = reader.readLine().split(";");
				if(line.length != 4)
					throw new IOException("Wrong database");
				
				String company = line[0];
				String user = line[1];
				int tries;
				int rate;
				try {
					tries = Integer.parseInt(line[2]);
					rate = Integer.parseInt(line[3]);
				} catch(NumberFormatException e) {
					throw new IOException("Wrong database");
				}
				
				HashSet<String> users = companies_info.get(line[0]);
				if(users == null) {
					users = new HashSet<String>();
					companies_info.put(company, users);
				}
				
				users.add(user);
				
				Integer[] user_info = users_info.get(user);
				if(user_info == null) {
					user_info = new Integer[] {tries, rate};
					users_info.put(user, user_info);
				}
			}
		} catch(FileNotFoundException e) {
			System.err.println("Can't load database caused to\n" + e.getMessage());
		}
	}
	
	public void close() {
		//закрываем, обновляя бд
		try(BufferedWriter writer = new BufferedWriter(new FileWriter(path));){
			for(Entry<String, HashSet<String>> comps : companies_info.entrySet()) {
				for(String usrs : comps.getValue()) {
					writer.write(comps.getKey() + ";");
					writer.write(usrs + ";");
					Integer[] v = users_info.get(usrs);
					writer.write(Integer.toString(v[0]) + ";");
					writer.write(Integer.toString(v[1]) + "\n");
					writer.flush();
				}
			}
		} catch(IOException e) {
			System.err.println("Can't save database caused to\n" + e.getMessage());
		}
	}
}
