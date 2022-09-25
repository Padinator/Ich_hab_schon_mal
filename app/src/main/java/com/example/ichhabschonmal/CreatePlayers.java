package com.example.ichhabschonmal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.room.Room;

import com.example.ichhabschonmal.database.AppDatabase;
import com.example.ichhabschonmal.database.Game;
import com.example.ichhabschonmal.database.Player;
import com.example.ichhabschonmal.database.Story;
import com.example.ichhabschonmal.exceptions.GamerException;
import com.example.ichhabschonmal.server_client_communication.Client;
import com.example.ichhabschonmal.server_client_communication.ClientServerHandler;
import com.example.ichhabschonmal.server_client_communication.ClientSocketEndPoint;
import com.example.ichhabschonmal.server_client_communication.ServerSocketEndPoint;
import com.example.ichhabschonmal.server_client_communication.SocketCommunicator;
import com.example.ichhabschonmal.server_client_communication.SocketEndPoint;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class CreatePlayers extends AppCompatActivity {

    private final LinkedList<Gamer> listOfPlayers = new LinkedList<>();
    private List<String> newListOfStories = new ArrayList<>();          // Is used for deleting/replacing stories in viewYourStories

    private int minStoryNumber, maxStoryNumber, maxPlayerNumber;
    private int idOfFirstPlayer = -1, countOfPlayers = 0, idOfFirstStory = -1, countOfStories = 0;
    private boolean alreadySavedOne = false;          // Check, if a player has saved his last story
    private boolean alreadySavedTwo = false;          // Check, if a player has saved changes of all stories

    private boolean onlineGame = false;     // Check, if actual game is an online game
    private boolean serverSide = false;     // Check, if actual device is the host/server
    private SocketCommunicator.Receiver receiverAction;


    private AppDatabase db;
    private ListView listView;
    private Button backButton, saveEditedStories;
    private ViewYourStoriesListAdapter adapter;
    private TextView storyNumber;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_players);

        // Set dark mode to none
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // Definitions
        Button saveAndNextStory, nextPerson, viewYourStories, btnContinue, rules;
        EditText writeStories, playerName;
        TextView playerID, charsLeft;

        // Buttons:
        saveAndNextStory = findViewById(R.id.saveAndNextStory);
        nextPerson = findViewById(R.id.nextPerson);
        viewYourStories = findViewById(R.id.viewYourStories);
        rules = findViewById(R.id.rules);
        btnContinue = findViewById(R.id.next);

        // EditTexts:
        writeStories = findViewById(R.id.writeStories);
        playerName = findViewById(R.id.playerName);

        //TextViews:
        playerID = findViewById(R.id.playerID);
        storyNumber = findViewById(R.id.storyNumber);
        charsLeft = findViewById(R.id.charsLeft);

        // Get from last intent
        //setItemsCanFocus(true);
        onlineGame = getIntent().getExtras().getBoolean("OnlineGame");

        if (onlineGame) {
            int tmp = getIntent().getExtras().getInt("PlayersIndex");

            playerID.setText("Du bist Spieler " + (tmp + 1) + ":");
            serverSide = getIntent().getExtras().getBoolean("ServerSide");

            // Define a new action for receiving messages
            receiverAction = new SocketCommunicator(null, null, null, null, null).new Receiver() {

                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void action() {
                    if (serverSide) { // Receive message from all clients
                        String clientsMessage = receiverAction.getMessage();
                        String[] lines = clientsMessage.split("\n");
                        clientsMessage = lines[0];

                        if (clientsMessage.equals(SocketEndPoint.CREATED_PLAYER)) {
                            Gamer receivedPlayer;
                            String[] receivedStories = new String[lines.length - 2];

                            receivedPlayer = new Gamer(Integer.parseInt(lines[1]));
                            receivedPlayer.setName(lines[2]);

                            System.arraycopy(lines, 2, receivedStories, 0, lines.length - 2);
                            receivedStories = String.join("\n", receivedStories).split("\n" + SocketEndPoint.START_OF_A_STORY);
                            receivedStories[0] = receivedStories[0].substring(SocketEndPoint.START_OF_A_STORY.length());

                            for (String story : receivedStories)
                                receivedPlayer.addStory(story);

                            // Last player in list is the host
                            listOfPlayers.addFirst(receivedPlayer);
                        }
                    } else { // Receive message from host
                        // Actually nothing to receive -> use if-else, because of Request-Response for connection checks
                    }
                }

            };

            if (serverSide)
                ClientServerHandler.getServerEndPoint().receiveMessages(receiverAction);
            else
                ClientServerHandler.getClientEndPoint().receiveMessages(receiverAction);
        }

        minStoryNumber = getIntent().getExtras().getInt("MinStoryNumber");
        maxStoryNumber = getIntent().getExtras().getInt("MaxStoryNumber");
        maxPlayerNumber = getIntent().getExtras().getInt("PlayerNumber");

        // Set used variables
        listOfPlayers.add(new Gamer(1));

        /*
        // Calling the action bar
        ActionBar actionBar = getSupportActionBar();

        // Showing the back button in action bar
        actionBar.setDisplayHomeAsUpEnabled(true);
        */

        viewYourStories.setOnClickListener(this::openDialog);

        writeStories.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.toString().length() <= 250) {
                    charsLeft.setText(editable.toString().length() + "/250");
                }

            }
        });

        rules.setOnClickListener(view -> {
            Intent rules1 = new Intent(CreatePlayers.this, Rules.class);
            rules1.putExtra("GameIsLoaded", false);
            startActivity(rules1);
        });

        saveAndNextStory.setOnClickListener(v -> {

            // Add a players story
            if (listOfPlayers.getLast().getCountOfStories() == maxStoryNumber)
                Toast.makeText(CreatePlayers.this, "Du hast bereits genug Stories " +
                        "aufgeschrieben!", Toast.LENGTH_LONG).show();
            else if (writeStories.getText().toString().isEmpty())
                Toast.makeText(CreatePlayers.this, "Kein Text zum speichern!",
                        Toast.LENGTH_LONG).show();
            else if (writeStories.getText().toString().length() < 25)
                Toast.makeText(CreatePlayers.this,
                        "Eine Story muss aus mindestens 25 zeichen " + "bestehen.",
                        Toast.LENGTH_SHORT).show();
            else {          // Text field is okay
                listOfPlayers.getLast().addStory(writeStories.getText().toString());
                writeStories.setText("");
                storyNumber.setText("Story " + (listOfPlayers.getLast().getCountOfStories() + 1) + ":");

                Toast.makeText(CreatePlayers.this,
                        "Story " + listOfPlayers.getLast().getCountOfStories() + " gespeichert",
                        Toast.LENGTH_LONG).show();

                // Set used variable
                alreadySavedOne = false;
            }
        });

        nextPerson.setOnClickListener(v -> {
            // Check inserting a new player
            if (listOfPlayers.size() == maxPlayerNumber)
                Toast.makeText(CreatePlayers.this, "Es k\u00f6nnen keine weiteren Spieler teilnehmen!",
                        Toast.LENGTH_LONG).show();
            else if (listOfPlayers.getLast().getCountOfStories() < minStoryNumber) {
                Toast.makeText(CreatePlayers.this, "Spieler muss mindestens " + minStoryNumber + " Stories besitzen!",
                        Toast.LENGTH_LONG).show();
                if (!writeStories.getText().toString().equals(""))
                    Toast.makeText(this, "Die letzte Story wurde noch nicht gespeichert!", Toast.LENGTH_SHORT).show();
            } else if (listOfPlayers.size() > maxPlayerNumber)
                Toast.makeText(CreatePlayers.this, "Zu viele eingeloggte Spieler!", Toast.LENGTH_LONG).show();
            else if (!alreadySavedOne && !writeStories.getText().toString().equals("")) {
                Toast.makeText(this, "Die letzte Story wurde noch nicht gespeichert, einmaliger Hinweis!", Toast.LENGTH_SHORT).show();
                alreadySavedOne = true;
            } else if (playerName.getText().toString().isEmpty()) {
                Toast.makeText(CreatePlayers.this, "Spielername darf nicht leer sein!", Toast.LENGTH_SHORT).show();
            } else if (playerName.getText().toString().length() < 2)
                Toast.makeText(CreatePlayers.this, "Spielername muss aus mindestens 2 Zeichen bestehen!", Toast.LENGTH_SHORT).show();
            else {
                Gamer player;

                // Set name of a player
                listOfPlayers.getLast().setName(playerName.getText().toString());

                Toast.makeText(CreatePlayers.this, "Spieler " + listOfPlayers.getLast().getNumber() + " erfolgreich gespeichert",
                        Toast.LENGTH_LONG).show();

                // Insert a new player
                player = new Gamer(listOfPlayers.size() + 1);
                listOfPlayers.add(player);

                // Reset Text fields in new_game.xml
                playerID.setText("Du bist Spieler " + (listOfPlayers.size()) + ":");
                playerName.setText("");
                storyNumber.setText("Story 1:");
                writeStories.setText("");
            }
        });

        btnContinue.setOnClickListener(view -> {

            // Check, if all players meet all conditions
            if (listOfPlayers.size() < maxPlayerNumber)
                Toast.makeText(CreatePlayers.this, "Zu wenig eingeloggte Spieler", Toast.LENGTH_SHORT).show();
            else if (listOfPlayers.size() > maxPlayerNumber)
                Toast.makeText(CreatePlayers.this, "Zu viele eingeloggte Spieler", Toast.LENGTH_SHORT).show();
            else if (listOfPlayers.getLast().getCountOfStories() < minStoryNumber) {
                Toast.makeText(CreatePlayers.this, "Spieler " + listOfPlayers.size() + " besitzt zu wenig Storys!", Toast.LENGTH_SHORT).show();
                if (!writeStories.getText().toString().equals(""))
                    Toast.makeText(this, "Die letzte Story wurde noch nicht gespeichert!", Toast.LENGTH_SHORT).show();
            } else if (listOfPlayers.getLast().getCountOfStories() > maxStoryNumber)
                Toast.makeText(CreatePlayers.this, "Spieler " + listOfPlayers.size() + " besitzt zu viele Storys!", Toast.LENGTH_SHORT).show();
            else if (!alreadySavedOne && !writeStories.getText().toString().equals("")) {
                Toast.makeText(this, "Die letzte Story wurde noch nicht gespeichert, einmaliger Hinweis!", Toast.LENGTH_SHORT).show();
                alreadySavedOne = true;
            } else if (playerName.getText().toString().isEmpty())     // Check last player's name
                Toast.makeText(CreatePlayers.this, "Spielername darf nicht leer sein", Toast.LENGTH_SHORT).show();
            else if (playerName.getText().toString().length() < 2)      // Check last player's name
                Toast.makeText(CreatePlayers.this, "Spielername muss aus mindestens 2 Zeichen bestehen", Toast.LENGTH_SHORT).show();
            else if (onlineGame && !serverSide) { // Client sends player to host
                if (listOfPlayers.size() != 1)
                    Log.e("CreatePlayer as client", "More than one player in list!");
                else
                    ClientServerHandler.getClientEndPoint().sendMessage(SocketEndPoint.CREATED_PLAYER + "\n" + listOfPlayers.get(0).toString());
            } else { // Local game or online gamer on serverside

                // Definitions
                Intent playGame;
                int actualGameId;

                // Set name of the last player
                listOfPlayers.getLast().setName(playerName.getText().toString());

                Toast.makeText(CreatePlayers.this, "Spieler " + listOfPlayers.getLast().getNumber() + " erfolgreich gespeichert",
                        Toast.LENGTH_LONG).show();

                // Create database connection:
                db = Room.databaseBuilder(this, AppDatabase.class, "database").allowMainThreadQueries().build();

                // Create new game
                Game newGame = new Game();
                // newGame.gameId = newGame.gameId;         // Game id are set with autoincrement
                newGame.gameName = getIntent().getStringExtra("GameName");
                newGame.onlineGame = onlineGame;
                newGame.roundNumber = 1;
                newGame.actualDrinkOfTheGame = getIntent().getStringExtra("DrinkOfTheGame");
                db.gameDao().insert(newGame);

                // Set used variable
                actualGameId = db.gameDao().getAll().get(db.gameDao().getAll().size() - 1).gameId;

                // Insert all players and their stories
                for (int i = 0; i < listOfPlayers.size(); i++) {

                    // Create a player
                    Player newPlayer = new Player();
                    //listOfNewPlayers[i].playerId = listOfNewPlayers[i].playerId;      // Player id is set with autoincrement
                    newPlayer.name = listOfPlayers.get(i).getName();
                    newPlayer.playerNumber = listOfPlayers.get(i).getNumber();
                    newPlayer.gameId = actualGameId;
                    /*
                    newPlayer.score = 0;
                    newPlayer.countOfBeers = 0;
                    newPlayer.countOfVodka = 0;
                    newPlayer.countOfTequila = 0;
                    */

                    // Insert the player
                    db.playerDao().insert(newPlayer);

                    // Set used variables for newGame
                    countOfPlayers++;
                    if (i == 0)
                        idOfFirstPlayer = db.playerDao().getAll().get(db.playerDao().getAll().size() - 1).playerId;

                    for (int j = 0; j < listOfPlayers.get(i).getCountOfStories(); j++) {

                        // Create a player's story
                        Story newStory = new Story();
                        //listOfStories[i].storyId = listOfStories[i].storyId;        // Story id is set with autoincrement
                        try {
                            newStory.content = listOfPlayers.get(i).getStory(j);
                        } catch (GamerException ex) {
                            ex.printStackTrace();
                            Log.e("SaveInDatabaseFailed", Arrays.toString(ex.getStackTrace()) + ", Message: " + ex.getMessage());
                            newStory.content = ex.toString();
                        }
                        newStory.status = false;
                        newStory.guessedStatus = false;         // Set a "default value"
                        newStory.playerId = db.playerDao().getAll().get(db.playerDao().getAll().size() - 1).playerId;
                        newStory.guessingPerson = "";           // Set a "default value"

                        // Insert a story
                        db.storyDao().insert(newStory);

                        // Set used variable for newGame
                        countOfStories++;

                        if (i == 0 && j == 0)
                            idOfFirstStory = db.storyDao().getAll().get(db.storyDao().getAll().size() - 1).storyId;
                    }
                }

                // Update newGame
                newGame.gameId = actualGameId;
                newGame.idOfFirstPlayer = idOfFirstPlayer;
                newGame.countOfPlayers = countOfPlayers;
                newGame.idOfFirstStory = idOfFirstStory;
                newGame.countOfStories = countOfStories;
                db.gameDao().updateGame(newGame);

                // Close database connection
                db.close();

                // Start new activity
                playGame = new Intent(CreatePlayers.this, PlayGame.class);
                playGame.putExtra("GameId", actualGameId);
                playGame.putExtra("GameIsLoaded", false);

                startActivity(playGame);
                finish();
            }
        });

        // Deactivate nextPerson-Button
        if (onlineGame) {
            // 1. possibility: Deactivate nextPerson and reset layout btnContinue
            /*
            nextPerson.setVisibility(View.GONE);
            */

            // 2. possibility: Deactivate btnContinue and use onclick-action of bntContinue for nextPerson
            btnContinue.setVisibility(View.GONE);
            nextPerson.setBackgroundColor(getResources().getColor(R.color.orange_one));
            nextPerson.setText(btnContinue.getText());
            nextPerson.setOnClickListener(view -> btnContinue.callOnClick());
        }
    }

    private class ViewYourStoriesListAdapter extends ArrayAdapter<String> {
        private final Activity activity;
        private final List<EditText> editTexts = new ArrayList<>();

        public ViewYourStoriesListAdapter(Activity activity) {
            super(activity, R.layout.view_your_stories_list_item, newListOfStories);
            this.activity = activity;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public View getView(int position, View view, ViewGroup parent) {

            // Definitions and initializations
            LayoutInflater inflater = activity.getLayoutInflater();
            @SuppressLint({"ViewHolder", "InflateParams"}) View rowView = inflater.inflate(R.layout.view_your_stories_list_item, null, true);
            EditText storyText = rowView.findViewById(R.id.storyText);
            ImageButton deleteStory = rowView.findViewById(R.id.deleteStory);

            editTexts.add(storyText);

            // Set story text in the listview-item
            storyText.setText(newListOfStories.get(position));

            storyText.setOnTouchListener(new View.OnTouchListener() {
                @SuppressLint("ClickableViewAccessibility")
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                        if (view.requestFocus()) {
                            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);
                        }

                        return true;
                    }

                    return false;
                }
            });

            deleteStory.setOnClickListener(new View.OnClickListener() {     // Give delete button a function
                @SuppressLint("SetTextI18n")
                @Override
                public void onClick(View view) {

                    // Delete story
                    newListOfStories.remove(position);
                    editTexts.remove(position);
                    listView.invalidateViews();
                }
            });

            return rowView;
        }

    }


    public void openDialog(View v) {

        // Definitions
        AlertDialog.Builder builder;
        AlertDialog dialog;
        View row;

        // Initializations
        newListOfStories = listOfPlayers.getLast().getAllStories();
        builder = new AlertDialog.Builder(this);
        row = getLayoutInflater().inflate(R.layout.view_your_stories, null);
        listView = row.findViewById(R.id.myStories);
        adapter = new ViewYourStoriesListAdapter(CreatePlayers.this);
        listView.setAdapter(adapter);
        adapter.notifyDataSetChanged();
        builder.setView(row);
        dialog = builder.create();
        dialog.show();      // Statement have to be exactly here at thus line

        // First show dialog and then set the rest, then keyboard will be displayed
        dialog.getWindow().clearFlags(LayoutParams.FLAG_NOT_FOCUSABLE | LayoutParams.FLAG_ALT_FOCUSABLE_IM);    // First step
        dialog.getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE);     // Second step

        saveEditedStories = row.findViewById(R.id.saveEditedStories);
        backButton = row.findViewById(R.id.backButton);

        saveEditedStories.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<String> newListOfStories = new ArrayList<>();

                // Get all stories
                for (int i = 0; i < listView.getAdapter().getCount(); i++) {
                    newListOfStories.add(adapter.editTexts.get(i).getText().toString());
                }

                // Replace all stories
                listOfPlayers.getLast().replaceAllStories(newListOfStories);

                // Actualize layout
                storyNumber.setText("Story " + (listOfPlayers.getLast().getCountOfStories() + 1) + ":");
                //listView.invalidateViews();

                Toast.makeText(CreatePlayers.this, "Stories wurden erfolgreich \u00fcberarbeitet", Toast.LENGTH_SHORT).show();
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!alreadySavedTwo) {
                    Toast.makeText(CreatePlayers.this, "Nicht gespeicherte \u00c4nderungen gehen verloren!", Toast.LENGTH_SHORT).show();
                    alreadySavedTwo = true;
                } else {
                    dialog.cancel();
                    alreadySavedTwo = false;
                }
            }
        });
    }


    @Override
    public void onBackPressed() {       // Catch back button
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Spielererstellung")
                .setMessage("Wenn du zur\u00fcck gehst, werden die Daten nicht gespeichert!")
                .setPositiveButton("Zur\u00fcck", (dialog, which) -> {
                    Intent mainActivity = new Intent(CreatePlayers.this, MainActivity.class);

                    try {
                        if (onlineGame && serverSide) {
                            ClientServerHandler.getServerEndPoint().disconnectClientsFromServer(); // Disconnect all clients from serve, serverside
                            ClientServerHandler.getServerEndPoint().disconnectServerSocket(); // Disconnect socket of server
                        } else if (onlineGame)
                            ClientServerHandler.getClientEndPoint().disconnectClient(); // Disconnect client from server, clientside
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    startActivity(mainActivity);
                    finish();
                })
                .setNegativeButton("Abbrechen", (dialogInterface, i) -> {

                });

        builder.create().show();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}