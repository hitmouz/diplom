terraform {
  backend "gcs" {
    bucket = "diplom-06-2024"
    prefix = "state"
  }
}

