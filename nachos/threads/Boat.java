package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
    static BoatGrader bg;
    static boolean isRowerSet;
    static boolean isRiderSet;
    static int population;
    
    static boolean isAdult;
    static Lock move;
    static Condition rowerMove;
    static Condition riderMove;
    static Condition passengerMove;
    static Condition mainMove;
    static Condition waitingMove;
    
    
    public static void selfTest()
    {
	BoatGrader b = new BoatGrader();	
	begin(4, 3, b);
    }

    public static void begin( int adults, int children, BoatGrader b )
    {
	// Store the externally generated autograder in a class
	// variable to be accessible by children.
	bg = b;
	isRowerSet = false;
	isRiderSet = false;
	population = 0;
	
	move = new Lock();
	riderMove = new Condition(move);
	rowerMove = new Condition(move);
	passengerMove = new Condition(move);
	waitingMove = new Condition(move);
	mainMove = new Condition(move);
	

	// Instantiate global variables here
	
	// Create threads here. See section 3.4 of the Nachos for Java
	// Walkthrough linked from the projects page.


	move.acquire();
	
	Runnable child = new Runnable() {
	    public void run() {
            ChildItinerary();
        }
    };
    
	Runnable adult = new Runnable() {
	    public void run() {
            AdultItinerary();
        }
    };
    
    for(int i = 0; i < children; i ++)
    {
    	KThread t = new KThread(child);
    	t.setName("Child Thread " + i);
    	t.fork();
    	mainMove.sleep();
    }
	for(int i = 0; i < adults; i ++)
	{
    	KThread t = new KThread(adult);
    	t.setName("Adult Thread " + i);
    	t.fork();
    	mainMove.sleep();
	}
	rowerMove.wake();
	mainMove.sleep();
    }

    static void AdultItinerary()
    {
	bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
	//DO NOT PUT ANYTHING ABOVE THIS LINE. 

	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	*/
	move.acquire();
	population ++;
	mainMove.wake();
	waitingMove.sleep();
	isAdult = true;
	rowerMove.wake();
	passengerMove.sleep();
	bg.AdultRowToMolokai();
	population --;
	rowerMove.wake();
    move.release();
    }

    static void ChildItinerary()
    {
	bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
	//DO NOT PUT ANYTHING ABOVE THIS LINE. 
	move.acquire();
	population ++;
	if(isRowerSet == false)
	{
		isRowerSet = true;
		Rower();
	}
	else if(isRiderSet == false)
	{

		isRiderSet = true;
		Rider();
	}
	else
	{
		mainMove.wake();
		waitingMove.sleep();
		isAdult = false;
		rowerMove.wake();
		passengerMove.sleep();
		bg.ChildRideToMolokai();
		population --;
		rowerMove.wake();
	}
    move.release();
    }

    static void Rower()
    {
    	mainMove.wake();
    	rowerMove.sleep();
    	while(population >= 3)
    	{
    		waitingMove.wake();
    		rowerMove.sleep();
    		if(isAdult == true)
    		{
    			bg.ChildRowToMolokai();
    			riderMove.wake();
    			rowerMove.sleep();
    			passengerMove.wake();
    			rowerMove.sleep();
    			bg.ChildRowToOahu();
    		}
    		else
    		{

    			bg.ChildRowToMolokai();
    			passengerMove.wake();
    			rowerMove.sleep();
    			bg.ChildRowToOahu();
    		}
    	}
    	bg.ChildRowToMolokai();
    	riderMove.wake();
    }
 
    static void Rider()
    {
    	mainMove.wake();
    	riderMove.sleep();
    	while(population >= 3)
    	{

    		bg.ChildRideToMolokai();
    		bg.ChildRowToOahu();
    		rowerMove.wake();
    		riderMove.sleep();
    	}
    	bg.ChildRideToMolokai();
    	mainMove.wake();
    }
}
