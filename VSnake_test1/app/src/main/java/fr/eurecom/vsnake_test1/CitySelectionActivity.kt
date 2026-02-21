package fr.eurecom.vsnake_test1

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class CitySelectionActivity : AppCompatActivity() {

    private var selectedCity: City? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_city_selection)

        val listView = findViewById<ListView>(R.id.cityListView)
        val confirmBtn = findViewById<Button>(R.id.confirmCityButton)

        val cities = City.values().map { it.name }

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_single_choice,
            cities
        )

        listView.choiceMode = ListView.CHOICE_MODE_SINGLE

        listView.setOnItemClickListener { _, _, position, _ ->
            selectedCity = City.values()[position]
        }

        confirmBtn.setOnClickListener {
            selectedCity?.let {

                val intent = Intent(this, MainMenuActivity::class.java)
                intent.putExtra("selectedCity", it.name)
                startActivity(intent)

                finish()
            }
        }

    }
}
