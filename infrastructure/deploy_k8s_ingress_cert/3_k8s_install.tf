
# Включение службы Google Kubernetes Engine
resource "google_project_service" "container" {
  service = "container.googleapis.com"
}

# Создание сети k8s кластера
resource "google_compute_network" "diplom_gke_network" {
  name                    = "diplom-gke-network"
  auto_create_subnetworks = false
}

# Создание подсети k8s кластера
resource "google_compute_subnetwork" "diplom_gke_subnetwork" {
  name                     = "diplom-gke-subnetwork"
  ip_cidr_range            = "10.3.0.0/24"
  network                  = google_compute_network.diplom_gke_network.self_link
  private_ip_google_access = true
  region                   = "europe-west3"
}

# Создание Cloud Router
resource "google_compute_router" "diplom_router" {
  name    = "diplom-router"
  network = google_compute_network.diplom_gke_network.name
  region  = "europe-west3"
}

# Создание Cloud NAT
resource "google_compute_router_nat" "diplom_nat" {
  name                     = "diplom-nat"
  router                   = google_compute_router.diplom_router.name
  region                   = google_compute_router.diplom_router.region
  nat_ip_allocate_option   = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"

  // Конфигурация времени ожидания для соединений
  enable_endpoint_independent_mapping = true

  // Перечисление параметров времени ожидания для различных типов соединений
  tcp_time_wait_timeout_sec = 30
  udp_idle_timeout_sec      = 30
  icmp_idle_timeout_sec     = 30
}

# Создание кластера Kubernetes
resource "google_container_cluster" "diplom_gke_cluster" {          
  name               = "diplom-gke-cluster"
  location           = "europe-west3-a"
  initial_node_count = 1
  network    = google_compute_network.diplom_gke_network.self_link
  subnetwork = google_compute_subnetwork.diplom_gke_subnetwork.self_link

  # Настройки machine type
  node_config {
    machine_type = "e2-small"

    # Настройки метаданных узлов
    metadata = {
      disable-legacy-endpoints = "true"
    }

    # Настройки ограничений ресурсов узлов
    oauth_scopes = [
      "https://www.googleapis.com/auth/logging.write",
      "https://www.googleapis.com/auth/monitoring",
    ]
  }

  # Включение приватного кластера (без публичного IP)
  private_cluster_config {
    enable_private_nodes    = true
    enable_private_endpoint = false
    master_ipv4_cidr_block  = "172.16.0.0/28"
  }

  # Включение логирования и мониторинга для кластера
  logging_service    = "logging.googleapis.com/kubernetes"
  monitoring_service = "monitoring.googleapis.com/kubernetes"

  # Отключение защиты от удаления
  deletion_protection = false
}

# Создание пула узлов в кластере
resource "google_container_node_pool" "diplom_gke_nodes" {
  name       = "diplom-gke-nodes"
  cluster    = google_container_cluster.diplom_gke_cluster.name
  location   = google_container_cluster.diplom_gke_cluster.location
  node_count = 1

  # Конфигурация узлов пула
  node_config {
    preemptible  = false
    machine_type = "e2-medium"

    labels = {
      role = "diplom_gke_nodes"
    }
    
    # Cписок OAuth-областей видимости для узлов пула
    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform",
    ]
  }

  # Автоматическое исправление и обновления узлов
  management {
    auto_repair  = true
    auto_upgrade = true
  }
}
  


