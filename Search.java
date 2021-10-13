import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Scanner;

/*
 * Nick Ubert CS452 - HW01
 */

public class Search {

	static Hashtable<String, City> cities = new Hashtable<String, City>();

	public static void main(String[] args) {
		// Required args
		String fileName = "error";
		String initCityName = "error";
		String goalCityName = "error";

		// Optional arg, Default to A* search
		String algoName = "a-star";
		// Optional args, Default to reached table allowed.
		boolean reachedAllowed = true;

		// Read in flags from cmd line, valid inputs are assumed.
		for (int i = 0; i < args.length; i += 2) {
			switch (args[i]) {
			case "-f":
				fileName = args[i + 1];
				break;
			case "-i":
				if (args[i + 1].contains("\"")) {
					initCityName = args[i + 1] + args[i + 2];
					initCityName.replaceAll("\"", "");
					i += 1;
				} else {
					initCityName = args[i + 1];
				}
				break;
			case "-g":
				if (args[i + 1].contains("\"")) {
					goalCityName = args[i + 1] + args[i + 2];
					goalCityName.replaceAll("\"", "");
					i += 1;
				}
				goalCityName = args[i + 1];
				break;
			case "-s":
				algoName = args[i + 1];
				break;
			case "--no-reached":
				reachedAllowed = false;
				i -= 1;
			}
		}

		try {
			cities = readFile(fileName);
		} catch (FileNotFoundException e) {
			System.out.println("#invalid file name passed in");
			e.printStackTrace();
		}

		PathResult n = new PathResult(null, 0, 0);
		n = search(initCityName, goalCityName, reachedAllowed, algoName);
		
		//No path found print
		if (n.node == null) {
			System.out.println("No path found");
			System.out.println("Distance: -1");
			System.out.println("Total nodes generated\t\t:" + n.nodesGen);
			System.out.println("Nodes remaining on frontier\t:" + n.frontierSize);
			System.exit(1);
		}
		
		//Get path values
		double totalCost = 0;
		ArrayList<PathNode> list = new ArrayList<PathNode>();
		while (n.node.parent != null) {
			list.add(n.node);

			totalCost += n.node.stepCost;
			n.node = n.node.parent;
		}
		list.add(n.node);
		
		//Print out solution
		System.out.print("Route Found:  ");
		for (int i = list.size()-1; i > 0; i--) {
			System.out.print(list.get(i).city.name + " -> ");
		}
		System.out.println(list.get(0).city.name);
		System.out.println("Distance: " + totalCost);
		System.out.println("Total nodes generated\t\t:" + n.nodesGen);
		System.out.println("Nodes remaining on frontier\t:" + n.frontierSize);

	}

	private static Hashtable<String, City> readFile(String fName) throws FileNotFoundException {
		// Initialize reading variables.
		Hashtable<String, City> result = new Hashtable<String, City>();
		File file = new File(fName);
		Scanner fscan = new Scanner(file);
		String line = fscan.nextLine();
		boolean distanceReading = false;

		// Loop through all lines in the file:
		while (fscan.hasNextLine()) {
			line = fscan.nextLine();

			if (line.equals("# distances")) {
				// Switch to distance reading after the comment is reached:
				distanceReading = true;
			} else {
				// Not changing read modes:
				if (distanceReading) {
					// Read input in distance format:

					// Create a scanner for the current line.
					Scanner lineScan = new Scanner(line);
					lineScan.useDelimiter(", |:");

					// Read in the three inputs we expect in order.
					String city1 = lineScan.next();
					String city2 = lineScan.next();
					double distance = Double.parseDouble(lineScan.next());
					// Add the distance value to both cities distance tables.
					result.get(city1).distance.put(city2, distance);
					result.get(city2).distance.put(city1, distance);
					lineScan.close();
				} else {
					// Read input in city format:

					String[] elements = line.split(" ");
					int len = elements.length;
					String name, lat, lon;

					// Lat and lon will appear at the end in order no matter what
					lon = elements[len - 1];
					lat = elements[len - 2];

					// Get city name. Avoid hanging space at the end.
					name = "";
					int index = 0;
					while (index < len - 3) {
						name += elements[index] + " ";
						index += 1;
					}
					name += elements[index];

					// Create a city object with the inputs.
					City curCity = new City(name, Double.parseDouble(lat), Double.parseDouble(lon));
					result.put(name, curCity);
				}
			}
		}

		fscan.close();

		return result;

	}

	private static double haversine(double lat1, double lon1, double lat2, double lon2) {
		// Convert to radians
		lat1 = (lat1 * Math.PI) / 180;
		lat2 = (lat2 * Math.PI) / 180;
		lon1 = (lon1 * Math.PI) / 180;
		lon2 = (lon2 * Math.PI) / 180;

		// Haversine
		double deltaLon = lon2 - lon1;
		double deltaLat = lat2 - lat1;
		double R = 3958.8;
		double a = Math.pow((Math.sin(deltaLat / 2)), 2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.pow((Math.sin(deltaLon / 2)), 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double d = R * c;
		return d;
	}

	//Return the total cost to reach a node from the root
	private static double getPathCost(PathNode node) {
		double runningTotal = 0;
		while (node != null) {
			if (node.parent != null) {
				runningTotal += node.city.distance.get(node.parent.city.name);
			}
			node = node.parent;
		}
		return runningTotal;
	}

	//Combine the path cost and heuristic values for a-star
	private static double getFValue(PathNode node, City newCity, City goalCity) {
		double hValue = haversine(newCity.latitude, newCity.longitude, goalCity.latitude, goalCity.longitude);
		double gValue = getPathCost(node);
		return hValue + gValue;
	}

	
	private static PathResult search(String init, String goal, boolean reachedAllowed, String algoName) {
		//Initialize queues and nodes
		PathNode startNode = new PathNode(null, cities.get(init), 0);
		Hashtable<String, PathNode> reached = new Hashtable<String, PathNode>();
		PriorityQueue<PathNode> frontier = new PriorityQueue<PathNode>();
		
		//Add root 
		reached.put(init, startNode);
		frontier.add(startNode);
		
		//Reference variables
		City goalCity = cities.get(goal);
		int nodesGen = 1;
		
		//Main loop
		while (!frontier.isEmpty()) {
			PathNode node = frontier.poll();
			//Check for goal state
			if (node.city.name.equals(goal)) {
				//Return a result object with all runtime variables needed
				return new PathResult(node, nodesGen, frontier.size());
			}

			//Expand node
			for (String s : node.city.distance.keySet()) {
				//Reference vars
				City newCity = cities.get(s);
				PathNode newNode = new PathNode(node, newCity, newCity.distance.get(node.city.name));
				
				//Cost selection method by the algorithm type
				switch (algoName) {
				case "uniform":
					newNode.cost = getPathCost(newNode);
					break;
				case "a-star":
					newNode.cost = getFValue(newNode, newCity, goalCity);
					break;
				case "greedy":
					newNode.cost = haversine(newCity.latitude, newCity.longitude, goalCity.latitude,
							goalCity.longitude);
					break;
				}

				//Check if the child is valid 
				if (!reachedAllowed || (!reached.containsKey(s)) || (reached.get(s).cost) > newNode.cost) {
					//Add to lists and increment runtime vars
					frontier.add(newNode);
					reached.put(s, newNode);
					nodesGen++;
				}
			}
		}
		//If the list is exhausted without finding a goal state
		return  new PathResult(null, nodesGen, frontier.size());

	}
}

class City {
	// City values
	String name;
	double latitude;
	double longitude;
	Hashtable<String, Double> distance;

	// Initialize a City
	public City(String n, double lat, double lon) {
		name = n;
		latitude = lat;
		longitude = lon;
		distance = new Hashtable<String, Double>();
	}

	public String toString() {
		return name + " (" + latitude + "," + longitude + ") size:" + distance.size();
	}

}

class PathNode implements Comparable<PathNode> {
	PathNode parent;
	City city;
	// Cost to reach this city from parent
	double stepCost;
	//Total cost of the node
	double cost;

	public PathNode(PathNode p, City c, double co) {
		parent = p;
		stepCost = co;
		city = c;
		cost = 0;
	}

	public int compareTo(PathNode other) {
		if (other.cost == this.cost) {
			return 0;
		}
		if (other.cost > this.cost) {
			return -1;
		} else {
			return 1;
		}
	}

}

//Helper class to return multiple variables easily 
class PathResult {
	PathNode node;
	int nodesGen;
	int frontierSize;

	public PathResult(PathNode n, int ng, int fs) {
		node = n;
		nodesGen = ng;
		frontierSize = fs;
	}
}
