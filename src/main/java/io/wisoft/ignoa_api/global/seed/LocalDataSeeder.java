package io.wisoft.ignoa_api.global.seed;

import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.ItemMedia;
import io.wisoft.ignoa_api.item.entity.enums.ItemCondition;
import io.wisoft.ignoa_api.item.entity.enums.ItemMediaType;
import io.wisoft.ignoa_api.item.repository.ItemMediaRepository;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import io.wisoft.ignoa_api.wish.entity.Wish;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Profile("local")
@Component
@RequiredArgsConstructor
public class LocalDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final ItemMediaRepository itemMediaRepository;
    private final WishRepository wishRepository;

    @Override
    public void run(String... args) throws Exception {

        if (itemRepository.count() > 100) return;

        List<User> users = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            users.add(new User("user" + i + "@test.com", "pw", "nick" + i, "주소" + i));
        }
        userRepository.saveAll(users);

        List<Item> items = new ArrayList<>();
        for (int i = 0; i < 30000; i++) {
            User seller = users.get(i % users.size());
            LocalDateTime endAt = LocalDateTime.now().plusDays((i % 30) + 1);
            items.add(Item.create(seller, "상품" + i, "설명", "카테고리" + (i % 5),
                    ItemCondition.GOOD, "브랜드", 1000L, 50000L, endAt));
        }
        itemRepository.saveAll(items);

        List<ItemMedia> medias = new ArrayList<>();
        for (Item item : items) {
            medias.add(ItemMedia.from(item, "https://dummy/img.jpg", ItemMediaType.IMAGE));
        }
        itemMediaRepository.saveAll(medias);

        List<Wish> wishes = new ArrayList<>();
        for (int idx = 0; idx < items.size(); idx++) {
            int wishCount = (idx < 50) ? 300 : (idx % 5);
            for (int u = 0; u < wishCount; u++) {
                wishes.add(Wish.create(users.get(u), items.get(idx)));
            }
        }
        wishRepository.saveAll(wishes);
    }
}


