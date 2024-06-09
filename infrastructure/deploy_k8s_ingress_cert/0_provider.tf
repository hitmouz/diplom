provider "google" {
  credentials = file("./vps2033-kuber50401033.json")
  project     = "vps2033"
  region      = "europe-west3"
}
