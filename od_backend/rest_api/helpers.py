from django.http import JsonResponse
from django.utils import timezone


def get_authenticated_user(request):
    auth_header = request.headers.get('Authorization', '')

    if not auth_header.startswith('Bearer '):
        return None, JsonResponse({'error': 'Authorization required'}, status=401)

    key = auth_header[7:]

    from .models import AuthToken
    try:
        token = AuthToken.objects.select_related('user').get(key=key, is_active=True)
    except AuthToken.DoesNotExist:
        return None, JsonResponse({'error': 'Invalid or inactive token'}, status=401)

    if token.expires_at and token.expires_at < timezone.now():
        return None, JsonResponse({'error': 'Token expired'}, status=401)

    return token.user, None