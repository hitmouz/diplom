# Подключение к Kubernetes
data "google_client_config" "default" {}

provider "kubernetes" {
  host                   = google_container_cluster.diplom_gke_cluster.endpoint
  token                  = data.google_client_config.default.access_token
  cluster_ca_certificate = base64decode(google_container_cluster.diplom_gke_cluster.master_auth.0.cluster_ca_certificate)
}

# Подключение к Helm
provider "helm" {
  kubernetes {
    host                   = google_container_cluster.diplom_gke_cluster.endpoint
    token                  = data.google_client_config.default.access_token
    cluster_ca_certificate = base64decode(google_container_cluster.diplom_gke_cluster.master_auth.0.cluster_ca_certificate)
  }
}

# Установка ingress-nginx
resource "helm_release" "nginx_ingress" {
  name       = "mynginx"
  repository = "https://kubernetes.github.io/ingress-nginx"
  chart      = "ingress-nginx"
  version    = "4.10.1"  # Укажите версию, которую вы хотите использовать
}

# Установка cert-manager через kubectl apply
resource "null_resource" "apply_cert_manager_yaml" {
  provisioner "local-exec" {
    command = "kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.15.0/cert-manager.yaml"
  }

  depends_on = [helm_release.nginx_ingress]
}
