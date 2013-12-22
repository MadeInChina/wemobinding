wemobinding
===========

An openhab binding for the Wemo Switch

To use this binding.

1) add the binding to openhab
2) add the following to the openhab.cfg file
  wemo:office.udn=Socket-1_0-XXXXXXXXXXXXXX 
  XXXXXXXXXXXXXX should be replaced with your wemo switch ID. If you do not know it, it should appear in the openhab log
  or in the console window when the binding discovers the switch.
  
From here it works the same as the Sonos binding.

3) In your demo.items file
  /* Demo items */
  Switch ChristmasTree      "Christmas Tree"      {wemo=">[ON:office:SetState], >[OFF:office:SetState]", autoupdate="true"}
  
4) In your demo.sitemap file
			Frame label="Binary Widgets" {
				Switch item=ChristmasTree mappings=[ON="ON", OFF="OFF"]
				Switch item=DemoSwitch label="Toggle Switch"
				Switch item=DemoSwitch label="Button Switch" mappings=[ON="On"]
			}

You can put the switch where you like. I happened to use the demo site and the demo config. Then I just added my switch to
the widgets section of the main menu. http://localhost:8080/openhab.app?sitemap=demo
